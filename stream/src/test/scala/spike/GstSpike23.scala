package spike

import fabric.Str
import fabric.io.{JsonFormatter, JsonParser}
import org.freedesktop.gstreamer.webrtc.{WebRTCBin, WebRTCSessionDescription}
import org.freedesktop.gstreamer.{Bus, Gst, GstObject, SDPMessage, State, Version}
import rapid._
import robobrowser.stream.gst.WebRTCDataChannel
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.DurationInt

/** Spikes S2+S3: prove webrtcbin works end-to-end from the JVM.
  *
  * Server side: videotestsrc -> x264enc -> webrtcbin (server offers), plus a
  * server-created DataChannel via our custom [[WebRTCDataChannel]] wrapper
  * (S3's typed `create-data-channel` emit through the ServiceLoader-registered
  * GType). Client side: a headless RoboBrowser running RTCPeerConnection, with
  * signaling bridged over CDP eval polling — no websocket server needed.
  *
  * Asserts: connectionState == connected, decoded video frames advancing, and
  * a DataChannel round-trip (server "hello" -> client echo -> server receipt).
  * Run: sbt "stream/Test/runMain spike.GstSpike23" */
object GstSpike23 extends RapidApp {
  private val ClientPage =
    """window.signals = [];
      |window.state = {conn: "", frames: 0, dcOpen: false, received: []};
      |const pc = new RTCPeerConnection({iceServers: [{urls: "stun:stun.l.google.com:19302"}]});
      |window.pc = pc;
      |pc.onicecandidate = e => {
      |  if (e.candidate && e.candidate.candidate) {
      |    window.signals.push(JSON.stringify({type: "ice", sdpMLineIndex: e.candidate.sdpMLineIndex, candidate: e.candidate.candidate}));
      |  }
      |};
      |pc.ontrack = e => {
      |  const v = document.createElement('video');
      |  v.autoplay = true; v.muted = true; v.playsInline = true;
      |  document.body.appendChild(v);
      |  v.srcObject = e.streams[0];
      |  setInterval(() => {
      |    if (v.getVideoPlaybackQuality) window.state.frames = v.getVideoPlaybackQuality().totalVideoFrames;
      |  }, 200);
      |};
      |pc.ondatachannel = e => {
      |  window.state.dcOpen = true;
      |  e.channel.onmessage = m => {
      |    window.state.received.push(m.data);
      |    e.channel.send("echo:" + m.data);
      |  };
      |};
      |pc.onconnectionstatechange = () => { window.state.conn = pc.connectionState; };
      |window.acceptOffer = async (sdp) => {
      |  await pc.setRemoteDescription({type: "offer", sdp: sdp});
      |  const answer = await pc.createAnswer();
      |  await pc.setLocalDescription(answer);
      |  window.signals.push(JSON.stringify({type: "answer", sdp: answer.sdp}));
      |};
      |window.addIce = async (idx, cand) => {
      |  await pc.addIceCandidate({sdpMLineIndex: idx, candidate: cand});
      |};
      |return 'ready';
      |""".stripMargin

  private def js(value: String): String = JsonFormatter.Compact(Str(value))

  override def run(args: List[String]): Task[Unit] = {
    Gst.init(Version.of(1, 18), "robobrowser-spike23")
    val pipeline = Gst.parseLaunch(
      "webrtcbin name=webrtc bundle-policy=max-bundle latency=40 stun-server=stun://stun.l.google.com:19302 " +
        "videotestsrc is-live=true pattern=ball ! videoconvert ! " +
        "x264enc tune=zerolatency speed-preset=ultrafast bitrate=2000 key-int-max=60 bframes=0 ! " +
        "video/x-h264,profile=constrained-baseline ! h264parse config-interval=-1 ! " +
        "rtph264pay pt=96 config-interval=-1 ! " +
        "application/x-rtp,media=video,encoding-name=H264,payload=96 ! webrtc."
    ).asInstanceOf[org.freedesktop.gstreamer.Pipeline]
    val webrtc = pipeline.getElementByName("webrtc").asInstanceOf[WebRTCBin]
    println("S2: WebRTCBin cast from parseLaunch OK")

    val offerSdp = new AtomicReference[Option[String]](None)
    val outIce = new java.util.concurrent.ConcurrentLinkedQueue[(Int, String)]()
    val dcEcho = new AtomicReference[Option[String]](None)

    pipeline.getBus.connect(new Bus.ERROR {
      override def errorMessage(source: GstObject, code: Int, message: String): Unit =
        println(s"S2: BUS ERROR from ${source.getName}: $message")
    })
    webrtc.connect(new WebRTCBin.ON_ICE_CANDIDATE {
      override def onIceCandidate(sdpMLineIndex: Int, candidate: String): Unit =
        outIce.add((sdpMLineIndex, candidate))
    })
    webrtc.connect(new WebRTCBin.ON_NEGOTIATION_NEEDED {
      override def onNegotiationNeeded(elem: org.freedesktop.gstreamer.Element): Unit = {
        println("S2: on-negotiation-needed fired")
        webrtc.createOffer(new WebRTCBin.CREATE_OFFER {
          override def onOfferCreated(offer: WebRTCSessionDescription): Unit = {
            webrtc.setLocalDescription(offer)
            offerSdp.set(Some(offer.getSDPMessage.toString))
            println("S2: offer created + local description set")
          }
        })
      }
    })

    // S3: typed create-data-channel emit through our registered wrapper type
    pipeline.setState(State.READY)
    val channel = webrtc.emit(classOf[WebRTCDataChannel], "create-data-channel", "input", null)
    require(channel != null, "create-data-channel returned null")
    println(s"S3: DataChannel created via typed emit, label=${channel.label}")
    channel.onMessageString(new WebRTCDataChannel.OnMessageString {
      override def onMessage(message: String): Unit = {
        println(s"S3: server received on DataChannel: $message")
        dcEcho.set(Some(message))
      }
    })
    channel.onOpen(new WebRTCDataChannel.OnOpen {
      override def onOpen(): Unit = {
        println("S3: DataChannel open, sending hello")
        channel.sendString("hello")
      }
    })
    pipeline.setState(State.PLAYING)

    def poll(browser: RoboBrowser): Task[Unit] = for {
      // Drain browser -> server signals
      drained <- browser.eval("return JSON.stringify(window.signals.splice(0))")
        .map(_("result")("value").asString)
      _ <- Task {
        JsonParser(drained).asVector.foreach { j =>
          val msg = JsonParser(j.asString)
          msg("type").asString match {
            case "answer" =>
              val sdp = new SDPMessage()
              sdp.parseBuffer(msg("sdp").asString)
              webrtc.setRemoteDescription(new WebRTCSessionDescription(
                org.freedesktop.gstreamer.webrtc.WebRTCSDPType.ANSWER, sdp))
              println("S2: remote answer applied")
            case "ice" =>
              webrtc.addIceCandidate(msg("sdpMLineIndex").asInt, msg("candidate").asString)
            case other => println(s"S2: unexpected client signal: $other")
          }
        }
      }
      // Push server -> browser ICE
      _ <- Task {
        Iterator.continually(outIce.poll()).takeWhile(_ != null).toList
      }.flatMap { candidates =>
        candidates.map { case (idx, cand) =>
          browser.eval(s"return window.addIce($idx, ${js(cand)})", awaitPromise = true).unit
        }.tasks.unit
      }
    } yield ()

    def waitFor(browser: RoboBrowser, description: String, deadline: Long)(check: Task[Boolean]): Task[Unit] =
      check.flatMap {
        case true => logger.info(s"S2: $description OK")
        case false if System.currentTimeMillis() > deadline =>
          Task.error(new RuntimeException(s"Timed out waiting for $description"))
        case false => poll(browser).flatMap(_ => Task.sleep(250.millis))
          .flatMap(_ => waitFor(browser, description, deadline)(check))
      }

    RoboBrowser.withBrowser(RoboBrowserConfig(
      browserConfig = BrowserConfig(noSandbox = true, disableGPU = true),
      tabSelector = TabSelector.FirstPage
    )) { browser =>
      val deadline = System.currentTimeMillis() + 45000
      for {
        _ <- browser.navigate("about:blank")
        _ <- browser.eval(ClientPage).map(j => require(j("result")("value").asString == "ready", "client page setup failed"))
        _ <- waitFor(browser, "offer produced", deadline)(Task(offerSdp.get().isDefined))
        _ <- browser.eval(s"return window.acceptOffer(${js(offerSdp.get().get)})", awaitPromise = true)
        _ <- waitFor(browser, "connection state connected", deadline) {
          browser.eval("return window.state.conn").map(_("result")("value").asString == "connected")
        }
        _ <- waitFor(browser, "video frames decoding", deadline) {
          browser.eval("return window.state.frames").map(_("result")("value").asInt > 10)
        }
        _ <- waitFor(browser, "datachannel echo round-trip", deadline)(Task(dcEcho.get().contains("echo:hello")))
        _ <- logger.info("S2+S3 PASSED: webrtcbin video + custom DataChannel wrapper verified from JVM")
      } yield ()
    }.guarantee(Task {
      pipeline.setState(State.NULL)
      pipeline.dispose()
    })
  }
}
