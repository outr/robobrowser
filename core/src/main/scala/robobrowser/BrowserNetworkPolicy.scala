package robobrowser

import java.net.{IDN, InetAddress, InetSocketAddress, ServerSocket, Socket}
import java.io.{DataInputStream, EOFException}
import java.util.concurrent.{ConcurrentHashMap, Executors, Semaphore}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import scala.jdk.CollectionConverters.*

/** Destination policy, enforced at connection time rather than by observing CDP events.
  * Empty host/port lists allow any host/port. Private addresses require an explicit opt-in.
  * Host patterns are exact names or *.domain (subdomains only); IP checks still apply. */
final case class BrowserNetworkPolicy(allowedHosts: List[String] = Nil,
                                      allowedPorts: List[Int] = Nil,
                                      allowPrivateNetworks: Boolean = false) {
  require(allowedHosts.size <= 256 && allowedPorts.size <= 256, "Network policy is too large")
  require(allowedPorts.forall(p => p > 0 && p <= 65535), "Invalid allowed port")
  private val hosts = allowedHosts.map { raw =>
    require(raw.nonEmpty && raw.length <= 253 && !raw.exists(c => c.isWhitespace || "/\\@?#".contains(c)), "Invalid allowed host")
    val value = raw.toLowerCase(java.util.Locale.ROOT).stripSuffix(".")
    require(!value.contains('*') || (value.startsWith("*.") && !value.drop(1).contains('*')), "Invalid host wildcard")
    if (value.contains(':')) value else if(value.startsWith("*.")) "*." + IDN.toASCII(value.drop(2)) else IDN.toASCII(value)
  }
  def permitsHost(host: String, port: Int): Boolean = {
    val name = host.toLowerCase(java.util.Locale.ROOT).stripSuffix(".").stripPrefix("[").stripSuffix("]")
    port > 0 && port <= 65535 && (allowedPorts.isEmpty || allowedPorts.contains(port)) &&
      (hosts.isEmpty || hosts.exists(p => if(p.startsWith("*.")) name.endsWith(p.drop(1)) else p == name))
  }
  def permitsAddress(address: InetAddress): Boolean = {
    val b = address.getAddress.map(_ & 255)
    val public = if(b.length == 4) {
      !(b(0) == 0 || b(0) == 10 || b(0) == 127 || b(0) >= 224 ||
        (b(0) == 100 && b(1) >= 64 && b(1) <= 127) ||
        (b(0) == 169 && b(1) == 254) || (b(0) == 172 && b(1) >= 16 && b(1) <= 31) ||
        (b(0) == 192 && (b(1) == 168 || b(1) == 0 || (b(1) == 88 && b(2) == 99))) ||
        (b(0) == 198 && (b(1) == 18 || b(1) == 19 || (b(1) == 51 && b(2) == 100))) ||
        (b(0) == 203 && b(1) == 0 && b(2) == 113))
    } else {
      // Only global unicast; exclude transition/documentation ranges that can embed other destinations.
      (b(0) & 0xe0) == 0x20 && !(b(0) == 0x20 && (b(1) == 2 || (b(1) == 1 && (b(2) < 2 || (b(2) == 0x0d && b(3) == 0xb8)))))
    }
    !address.isAnyLocalAddress && !address.isMulticastAddress && (allowPrivateNetworks || public)
  }
  private[robobrowser] def resolve(host: String, port: Int): List[InetAddress] = {
    require(permitsHost(host, port), "Browser network destination denied")
    val addresses = InetAddress.getAllByName(host)
    require(addresses.nonEmpty && addresses.forall(permitsAddress), "Browser network address denied")
    addresses.toList // Connect only to these checked addresses; never resolve the hostname a second time.
  }
}

/** Bounded SOCKS5 CONNECT-only proxy. No UDP association, direct fallback, or DNS rebinding window.
  * TLS stays end-to-end between Chrome and the destination. This is not an OS process sandbox. */
final class BrowserNetworkProxy(policy: BrowserNetworkPolicy) extends AutoCloseable {
  private val server = new ServerSocket()
  server.bind(new InetSocketAddress("127.0.0.1", 0), 128)
  val port: Int = server.getLocalPort
  val accepted = new AtomicLong()
  val denied = new AtomicLong()
  private val closed = new AtomicBoolean()
  private val permits = new Semaphore(128)
  private val sockets = ConcurrentHashMap.newKeySet[Socket]()
  private val workers = Executors.newVirtualThreadPerTaskExecutor()
  private val acceptor = Thread.ofVirtual().name("browser-network-policy").start(() => {
    while(!closed.get()) {
      try {
        val socket = server.accept()
        if(!permits.tryAcquire()) socket.close()
        else {
          sockets.add(socket)
          try workers.submit(new Runnable { def run(): Unit = try connect(socket) finally { sockets.remove(socket); socket.close(); permits.release() } })
          catch { case _: java.util.concurrent.RejectedExecutionException => sockets.remove(socket);socket.close();permits.release() }
        }
      } catch { case _: java.net.SocketException if closed.get() => () }
    }
  })
  def configure(config: BrowserConfig): BrowserConfig = {
    require(config.proxyServer.isEmpty && config.proxyCredentials.isEmpty && config.proxyBypassList.isEmpty, "Network policy owns browser proxy configuration")
    require(!config.extraArgs.exists(a => List("--proxy", "--no-proxy", "--host-resolver", "--webrtc", "--force-webrtc", "--enable-quic").exists(a.startsWith)), "Network policy conflicts with browser switches")
    config.copy(proxyServer = Some(("socks5://127.0.0.1", port)), proxyBypassList = List("<-loopback>"),
      disableBackgroundNetworking = true,
      extraArgs = config.extraArgs ::: List("--disable-quic", "--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1",
        "--webrtc-ip-handling-policy=disable_non_proxied_udp"))
  }
  private def connect(client: Socket): Unit = {
    var upstream: Socket = null
    var established = false
    try {
      client.setSoTimeout(10000)
      val in = new DataInputStream(client.getInputStream)
      val out = client.getOutputStream
      require(in.readUnsignedByte() == 5, "SOCKS version")
      val methods = in.readNBytes(in.readUnsignedByte())
      require(methods.contains(0.toByte), "SOCKS authentication")
      out.write(Array[Byte](5, 0));out.flush()
      require(in.readUnsignedByte() == 5 && in.readUnsignedByte() == 1 && in.readUnsignedByte() == 0, "CONNECT required")
      val host = in.readUnsignedByte() match {
        case 1 => InetAddress.getByAddress(exact(in, 4)).getHostAddress
        case 4 => InetAddress.getByAddress(exact(in, 16)).getHostAddress
        case 3 => new String(exact(in, in.readUnsignedByte()), java.nio.charset.StandardCharsets.US_ASCII)
        case _ => throw new IllegalArgumentException("Address type")
      }
      val destinationPort = in.readUnsignedShort()
      val addresses = policy.resolve(host, destinationPort).iterator
      val deadline=System.nanoTime()+8000000000L
      var connected=false
      while(!connected&&addresses.hasNext&&System.nanoTime()<deadline) {
        upstream=new Socket();sockets.add(upstream)
        val remaining=math.max(1L,(deadline-System.nanoTime())/1000000L).min(2000L).toInt
        try {upstream.connect(new InetSocketAddress(addresses.next(),destinationPort),remaining);connected=true}
        catch {case _:java.io.IOException=>sockets.remove(upstream);upstream.close();upstream=null}
      }
      require(connected,"Browser network connection unavailable")
      client.setSoTimeout(120000);upstream.setSoTimeout(120000)
      out.write(Array[Byte](5, 0, 0, 1, 0, 0, 0, 0, 0, 0));out.flush()
      established = true;accepted.incrementAndGet()
      val remote = upstream
      val upload = workers.submit(new Runnable { def run(): Unit = try {
        in.transferTo(remote.getOutputStream);remote.shutdownOutput()
      } catch { case _: Exception => remote.close() } })
      try { remote.getInputStream.transferTo(out);client.shutdownOutput() } finally { remote.close();client.close();upload.cancel(true) }
    } catch {
      case _: Exception =>
        if(!established) {
          denied.incrementAndGet()
          try { client.getOutputStream.write(Array[Byte](5, 2, 0, 1, 0, 0, 0, 0, 0, 0));client.getOutputStream.flush() } catch { case _: Exception => () }
        }
    } finally if(upstream != null) { sockets.remove(upstream);upstream.close() }
  }
  private def exact(in: DataInputStream, length: Int): Array[Byte] = {
    require(length > 0, "Empty address")
    val bytes = in.readNBytes(length)
    if(bytes.length != length) throw new EOFException()
    bytes
  }
  def close(): Unit = if(closed.compareAndSet(false, true)) {
    server.close();sockets.asScala.foreach(s => try s.close() catch { case _: Exception => () });workers.shutdownNow()
  }
}
