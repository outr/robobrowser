package robobrowser.mirror

import org.scalatest.funsuite.AnyFunSuite
import robobrowser.{BrowserHost, BrowserNetworkPolicy, BrowserNetworkProxy}
import com.sun.net.httpserver.HttpServer
import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.atomic.AtomicInteger
import fabric.*

class BrowserNetworkPolicySpec extends AnyFunSuite {
  test("stalled SOCKS handshakes hit the connection limit and closing them restores admission") {
    val proxy=new BrowserNetworkProxy(BrowserNetworkPolicy())
    val clients=scala.collection.mutable.ListBuffer.empty[java.net.Socket]
    def greeting():java.net.Socket={
      val socket=new java.net.Socket("127.0.0.1",proxy.port);clients+=socket;socket.setSoTimeout(2000)
      socket.getOutputStream.write(Array[Byte](5,1,0))
      assert(socket.getInputStream.read()==5&&socket.getInputStream.read()==0)
      socket
    }
    try {
      for(_<-1 to 128)greeting()
      val excess=new java.net.Socket("127.0.0.1",proxy.port);excess.setSoTimeout(2000)
      try assert(excess.getInputStream.read()== -1,"Excess connection must close promptly")finally excess.close()
      clients.foreach(_.close());clients.clear()
      Thread.sleep(100)
      greeting()
    }finally{clients.foreach(_.close());proxy.close()}
  }
  test("host rules, ports, private and transition addresses fail closed") {
    val policy=BrowserNetworkPolicy(List("*.example.org", "127.0.0.1"),List(443))
    assert(policy.permitsHost("www.example.org",443))
    assert(!policy.permitsHost("example.org",443))
    assert(!policy.permitsHost("evil-example.org",443))
    assert(!policy.permitsHost("www.example.org",80))
    List("127.0.0.1","10.1.2.3","172.16.1.1","192.168.1.1","169.254.169.254","100.64.0.1","::1","fe80::1","fc00::1","::ffff:127.0.0.1","2002:7f00:1::","2001:db8::1").foreach { ip =>
      assert(!policy.permitsAddress(InetAddress.getByName(ip)),ip)
    }
    assert(policy.permitsAddress(InetAddress.getByName("8.8.8.8")))
    assert(policy.copy(allowPrivateNetworks=true).permitsAddress(InetAddress.getByName("127.0.0.1")))
    intercept[IllegalArgumentException]{BrowserNetworkPolicy(List("*example.org"))}
  }

  test("Chrome navigation, redirects, resources, workers and WebSockets cannot bypass destination policy") {
    val blockedHits=new AtomicInteger()
    val blocked=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0)
    blocked.createContext("/",e=>{blockedHits.incrementAndGet();e.getResponseHeaders.add("Access-Control-Allow-Origin","*");e.sendResponseHeaders(200,2);e.getResponseBody.write("ok".getBytes);e.close()});blocked.start()
    val denied=s"http://127.0.0.1:${blocked.getAddress.getPort}"
    val allowed=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0)
    val allowedPort=allowed.getAddress.getPort
    val base=s"http://127.0.0.1:$allowedPort"
    allowed.createContext("/",e=>{
      val (kind,body)=e.getRequestURI.getPath match {
        case "/redirect"=>e.getResponseHeaders.add("Location",denied+"/redirect-target");e.sendResponseHeaders(302,-1);e.close();("","")
        case "/worker.js"=>("application/javascript",s"fetch('$denied/worker').then(()=>postMessage('bypass'),()=>postMessage('blocked'))")
        case "/sw.js"=>("application/javascript",s"self.addEventListener('install',e=>self.skipWaiting());self.addEventListener('activate',e=>e.waitUntil(clients.claim()));self.addEventListener('message',e=>fetch('$denied/service-worker').then(()=>e.source.postMessage('bypass'),()=>e.source.postMessage('blocked')))")
        case _=>("text/html","<!doctype html><title>Allowed source</title><body>Allowed source</body>")
      }
      if(kind.nonEmpty){val bytes=body.getBytes("UTF-8");e.getResponseHeaders.add("Content-Type",kind);e.sendResponseHeaders(200,bytes.length);e.getResponseBody.write(bytes);e.close()}
    });allowed.start()
    val policy=BrowserNetworkPolicy(List("127.0.0.1"),List(allowedPort),allowPrivateNetworks=true)
    val proxy=new BrowserNetworkProxy(policy)
    val host=BrowserHost.start(proxy.configure(robobrowser.BrowserConfig())).sync()
    try {
      val tab=host.open(base).sync()
      val loadedBy=System.nanoTime()+5000000000L
      while(tab.evaluate("document.title").sync().asString!="Allowed source"&&System.nanoTime()<loadedBy)Thread.sleep(20)
      assert(tab.evaluate("document.title").sync().asString=="Allowed source")
      def js(code:String)=tab.evaluate(code).sync()
      assert(js(s"fetch('$denied/fetch').then(()=>false,()=>true)").asBoolean)
      assert(js(s"fetch('http://localhost:$allowedPort/host-denied').then(()=>false,()=>true)").asBoolean)
      assert(js(s"fetch('$base/redirect').then(()=>false,()=>true)").asBoolean)
      assert(js(s"new Promise(r=>{const i=new Image;i.onload=()=>r(false);i.onerror=()=>r(true);i.src='$denied/image';document.body.append(i)})").asBoolean)
      assert(js("new Promise(r=>{const w=new Worker('/worker.js');w.onmessage=e=>{w.terminate();r(e.data==='blocked')};w.onerror=()=>r(false)})").asBoolean)
      assert(js("navigator.serviceWorker.register('/sw.js').then(()=>navigator.serviceWorker.ready).then(()=>new Promise(r=>{navigator.serviceWorker.onmessage=e=>r(e.data==='blocked');navigator.serviceWorker.ready.then(s=>s.active.postMessage('go'))}))").asBoolean)
      assert(js(s"new Promise(r=>{const w=new WebSocket('${denied.replace("http:","ws:")}/socket');w.onopen=()=>{w.close();r(false)};w.onerror=()=>r(true)})").asBoolean)
      val udp=new java.net.DatagramSocket(new InetSocketAddress("127.0.0.1",0))
      try {
        js(s"(async()=>{const p=new RTCPeerConnection({iceServers:[{urls:'stun:127.0.0.1:${udp.getLocalPort}'}]});p.createDataChannel('policy');await p.setLocalDescription(await p.createOffer());await new Promise(r=>setTimeout(r,1500));p.close();return true})()")
        udp.setSoTimeout(200)
        intercept[java.net.SocketTimeoutException]{udp.receive(new java.net.DatagramPacket(new Array[Byte](2048),2048))}
      }finally udp.close()
      val before=proxy.denied.get()
      try host.open(denied+"/navigation").sync() catch {case _:Exception=>()}
      val deadline=System.nanoTime()+5000000000L
      while(proxy.denied.get()==before&&System.nanoTime()<deadline)Thread.sleep(50)
      assert(proxy.denied.get()>before,"Top-level navigation reaches policy")
      assert(blockedHits.get()==0,"Denied upstream must receive no HTTP request")
      assert(proxy.accepted.get()>0&&proxy.denied.get()>=7)
    } finally {host.stop().sync();proxy.close();allowed.stop(0);blocked.stop(0)}
  }

  test("TLS stays end-to-end through the proxy and proxy failure never falls back to direct traffic") {
    val directory=java.nio.file.Files.createTempDirectory("browser-network-tls-")
    val store=directory.resolve("fixture.p12")
    val keytool=java.nio.file.Path.of(System.getProperty("java.home"),"bin","keytool").toString
    val generated=new ProcessBuilder(keytool,"-genkeypair","-alias","fixture","-keyalg","RSA","-keystore",store.toString,"-storepass","fixture-only","-keypass","fixture-only","-dname","CN=localhost","-validity","1","-ext","SAN=ip:127.0.0.1").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    assert(generated.waitFor(20,java.util.concurrent.TimeUnit.SECONDS)&&generated.exitValue()==0)
    val keys=java.security.KeyStore.getInstance("PKCS12")
    val input=java.nio.file.Files.newInputStream(store);try keys.load(input,"fixture-only".toCharArray)finally input.close()
    val km=javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm);km.init(keys,"fixture-only".toCharArray)
    val tls=javax.net.ssl.SSLContext.getInstance("TLS");tls.init(km.getKeyManagers,null,null)
    val server=com.sun.net.httpserver.HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0)
    server.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(tls))
    val hits=new AtomicInteger()
    server.createContext("/",e=>{hits.incrementAndGet();val b="<!doctype html><title>TLS policy proof</title>".getBytes;e.sendResponseHeaders(200,b.length);e.getResponseBody.write(b);e.close()});server.start()
    val proxy=new BrowserNetworkProxy(BrowserNetworkPolicy(List("127.0.0.1"),List(server.getAddress.getPort),true))
    val host=BrowserHost.start(proxy.configure(robobrowser.BrowserConfig(ignoreCertificateErrors=true))).sync()
    try {
      val base=s"https://127.0.0.1:${server.getAddress.getPort}"
      val tab=host.open(base).sync()
      val deadline=System.nanoTime()+5000000000L
      while(tab.evaluate("document.title").sync().asString!="TLS policy proof"&&System.nanoTime()<deadline)Thread.sleep(25)
      assert(tab.evaluate("document.title").sync().asString=="TLS policy proof")
      assert(proxy.accepted.get()>0)
      proxy.close();Thread.sleep(100);val previous=hits.get()
      assert(tab.evaluate(s"fetch('$base/fresh',{cache:'no-store'}).then(()=>false,()=>true)").sync().asBoolean)
      assert(hits.get()==previous,"A failed proxy must not cause a direct connection")
    }finally{host.stop().sync();proxy.close();server.stop(0);java.nio.file.Files.deleteIfExists(store);java.nio.file.Files.deleteIfExists(directory)}
  }

  test("default policy denies loopback even when Chrome implicitly bypasses it without the policy") {
    val hits=new AtomicInteger()
    val upstream=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0)
    upstream.createContext("/",e=>{hits.incrementAndGet();e.sendResponseHeaders(200,2);e.getResponseBody.write("ok".getBytes);e.close()});upstream.start()
    val proxy=new BrowserNetworkProxy(BrowserNetworkPolicy())
    val host=BrowserHost.start(proxy.configure(robobrowser.BrowserConfig())).sync()
    try {
      host.open(s"http://127.0.0.1:${upstream.getAddress.getPort}/").sync()
      val end=System.nanoTime()+5000000000L
      while(proxy.denied.get()==0&&System.nanoTime()<end)Thread.sleep(50)
      assert(proxy.denied.get()>0&&hits.get()==0)
    }finally{host.stop().sync();proxy.close();upstream.stop(0)}
  }
}
