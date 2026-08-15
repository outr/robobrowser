package spec

import fabric.Str
import fabric.io.{JsonFormatter, JsonParser}
import fabric.rw._
import rapid._
import robobrowser.display.VirtualDisplayConfig
import robobrowser.stream.Stream.stream
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}

import scala.concurrent.duration.DurationInt

/** Automated end-to-end acceptance (M2+M3+M6): a virtual-display browser is
  * streamed via the demo server; a second, headless RoboBrowser acts as the
  * viewer. Asserts: WebRTC connects, video frames advance, a DataChannel click
  * lands on a page element in the streamed browser, typed keys arrive in its
  * focused input, a mid-stream resize keeps the very same peer connection alive
  * (no second offer, no state change, frames keep advancing at the new
  * resolution) and keeps it alive under a soak long enough for a disturbed
  * transport to surface, and the latency harness reports a plausible value.
  * Run: sbt "stream/Test/runMain spec.StreamE2ETest" */
object StreamE2ETest extends RapidApp {
  private val ResizeWidth = 960
  private val ResizeHeight = 600

  /** Long enough to cover an SCTP association timing out after a disturbance:
    * the failure mode this pins surfaced ~7s after a reconfigure. */
  private val SoakDuration = 15.seconds

  private val TestPage =
    "data:text/html,<html><body style='margin:0'>" +
      "<button id='b' style='position:absolute;left:100px;top:100px;width:200px;height:80px;font-size:30px' " +
      "onclick=\"document.title='clicked'; document.getElementById('i').focus()\">Click me</button>" +
      "<input id='i' style='position:absolute;left:100px;top:220px;width:200px;height:40px;font-size:20px'>" +
      "<div id='t' style='position:absolute;left:0;top:320px;font-size:40px'>0</div>" +
      "</body></html>"

  /** Repaint continuously so "frames are advancing" stays a real assertion:
    * `use-damage=true` means a static page streams almost nothing. */
  private val Animate =
    "window.setInterval(() => document.getElementById('t').textContent = Date.now(), 100); return true;"

  private def js(value: String): String = JsonFormatter.Compact(Str(value))

  override def run(args: List[String]): Task[Unit] =
    RoboBrowser.withBrowser(RoboBrowserConfig(
      browserConfig = BrowserConfig(noSandbox = true, disableGPU = true),
      tabSelector = TabSelector.FirstPage,
      virtualDisplay = Some(VirtualDisplayConfig(width = 1280, height = 720))
    )) { streamed =>
      val server = new StreamDemoServer(streamed, port = 8899,
        streamConfig = robobrowser.stream.StreamConfig(encoderOverride = sys.env.get("STREAM_ENCODER")))
      for {
        _ <- streamed.navigate(TestPage)
        _ <- streamed.waitForLoaded()
        _ <- streamed.eval(Animate)
        _ <- server.start()
        _ <- RoboBrowser.withBrowser(RoboBrowserConfig(
          browserConfig = BrowserConfig(noSandbox = true, disableGPU = true),
          tabSelector = TabSelector.FirstPage
        )) { viewer =>
          for {
            _ <- viewer.navigate("http://localhost:8899/")
            _ <- viewer.waitForLoaded()
            _ <- waitFor(viewer, "webrtc connected") {
              viewer.eval("return window.viewer ? window.viewer.pc.connectionState : ''")
                .map(_("result")("value").asString == "connected")
            }.handleError { t =>
              viewer.eval(
                """const mlines = sdp => sdp ? sdp.sdp.split('\r\n').filter(l => l.startsWith('m=') || l.startsWith('a=fingerprint') || l.startsWith('a=setup')) : [];
                  |return JSON.stringify({
                  |  log: window.viewer.log,
                  |  signaling: window.viewer.pc.signalingState,
                  |  ice: window.viewer.pc.iceConnectionState,
                  |  gathering: window.viewer.pc.iceGatheringState,
                  |  conn: window.viewer.pc.connectionState,
                  |  offerMLines: mlines(window.viewer.pc.remoteDescription),
                  |  answerMLines: mlines(window.viewer.pc.localDescription)
                  |})""".stripMargin)
                .flatMap(json => logger.error(s"E2E DIAG: ${json("result")("value").asString}"))
                .flatMap(_ => Task.error[Unit](t))
            }
            _ <- waitFor(viewer, "video frames advancing") {
              viewer.eval("return window.viewer.frames").map(_("result")("value").asInt > 10)
            }
            // Click the streamed page's button through the DataChannel (stream
            // pixels == viewport pixels at native size, so page coords pass through)
            _ <- viewer.eval(
              """window.viewer.dcSend({type: 'mousemove', x: 200, y: 140, buttons: 0});
                |window.viewer.dcSend({type: 'mousedown', x: 200, y: 140, button: 'left', buttons: 1, clickCount: 1, modifiers: 0});
                |window.viewer.dcSend({type: 'mouseup', x: 200, y: 140, button: 'left', buttons: 0, clickCount: 1, modifiers: 0});
                |return true;""".stripMargin)
            _ <- waitFor(viewer, "click routed to streamed browser") {
              streamed.title.map(_ == "clicked")
            }
            _ <- viewer.eval(
              """['h','i'].forEach(ch => {
                |  window.viewer.dcSend({type: 'keydown', key: ch, code: 'Key' + ch.toUpperCase(), keyCode: ch.toUpperCase().charCodeAt(0), text: '', modifiers: 0});
                |  window.viewer.dcSend({type: 'char', text: ch, key: ch, keyCode: ch.toUpperCase().charCodeAt(0)});
                |  window.viewer.dcSend({type: 'keyup', key: ch, code: 'Key' + ch.toUpperCase(), keyCode: ch.toUpperCase().charCodeAt(0), text: '', modifiers: 0});
                |});
                |return true;""".stripMargin)
            _ <- waitFor(viewer, "typed text arrived in streamed input") {
              streamed.eval("return document.getElementById('i').value").map(_("result")("value").asString == "hi")
            }
            _ <- resizeKeepsTheConnection(streamed, viewer)
            _ <- resizeSurvivesASoak(streamed, viewer)
            _ <- waitFor(viewer, "latency harness reporting") {
              viewer.eval("return window.viewer.latency === null ? -1 : window.viewer.latency")
                .map { json =>
                  val v = json("result")("value").asDouble
                  v >= 0 && v < 5000
                }
            }
            latency <- viewer.eval("return window.viewer.latency").map(_("result")("value").asDouble)
            statsJson <- streamed.stream.sessions.headOption match {
              case Some(session) => session.stats.map(s => JsonFormatter.Default(s.json))
              case None => Task.pure("no session")
            }
            _ <- logger.info(f"E2E PASSED — glass-to-glass latency: $latency%.0f ms")
            _ <- logger.info(s"E2E stats: $statsJson")
          } yield ()
        }
        _ <- server.stop()
      } yield ()
    }.flatMap { _ =>
      // After all browsers are disposed: Netty client event-loop threads are
      // non-daemon and would keep the forked JVM alive after main completes
      Task(System.exit(0))
    }

  /** A mid-stream resize reconfigures the live pipeline, so the peer connection
    * the viewer already has is the one it keeps: no second offer to answer, no
    * `connectionState` transition, and frames still advancing — at the new
    * resolution, which H.264 carries in-band. */
  private def resizeKeepsTheConnection(streamed: RoboBrowser, viewer: RoboBrowser): Task[Unit] = for {
    _ <- viewer.eval(
      """window.states = [];
        |window.viewer.pc.addEventListener('connectionstatechange',
        |  () => window.states.push(window.viewer.pc.connectionState));
        |return true;""".stripMargin)
    before <- viewer.eval("return window.viewer.frames").map(_("result")("value").asInt)
    session <- Task(streamed.stream.sessions.headOption.getOrElse(
      throw new RuntimeException("E2E: no live stream session to resize")))
    _ <- session.resize(ResizeWidth, ResizeHeight)
    _ <- waitFor(viewer, "frames advancing past the resize") {
      viewer.eval("return window.viewer.frames").map(_("result")("value").asInt > before + 10)
    }
    _ <- waitFor(viewer, s"viewer decoding ${ResizeWidth}x$ResizeHeight") {
      viewer.eval("return window.viewer.videoWidth").map(_("result")("value").asInt == ResizeWidth)
    }
    intact <- viewer.eval(
      """return window.viewer.pc.connectionState === 'connected' &&
        |  window.states.length === 0 &&
        |  window.viewer.log.filter(t => t === 'offer').length === 1;""".stripMargin)
      .map(_("result")("value").asBoolean)
    _ <- if (intact) {
      logger.info("E2E: resize kept the peer connection OK")
    } else {
      viewer.eval(
        """return JSON.stringify({
          |  connectionState: window.viewer.pc.connectionState,
          |  stateTrail: window.states,
          |  signalLog: window.viewer.log
          |})""".stripMargin)
        .map(_("result")("value").asString)
        .flatMap(diagnostics => Task.error[Unit](
          new RuntimeException(s"E2E: the resize disturbed the peer connection: $diagnostics")))
    }
  } yield ()

  /** The reconfigure's effects on the shared WebRTC transport are not immediate:
    * a disturbed SCTP association keeps reporting `open` and only fails once its
    * retransmissions time out, seconds after the resize returned. So the session
    * is left running with the capture tap writing its ~1Hz frame stamps and the
    * viewer pinging, then everything the resize is supposed to have left alone is
    * checked again: no pipeline error reached the bus, video still advancing, and
    * the input DataChannel still carrying traffic in both directions — ending
    * with a real click routed through it. */
  private def resizeSurvivesASoak(streamed: RoboBrowser, viewer: RoboBrowser): Task[Unit] = for {
    _ <- waitFor(viewer, "capture-tap frame stamps arriving over the DataChannel") {
      viewer.eval("return window.viewer.frameStamps").map(_("result")("value").asInt > 0)
    }
    _ <- logger.info(s"E2E: soaking ${SoakDuration.toSeconds}s after the resize")
    _ <- Task.sleep(SoakDuration)
    _ <- viewer.eval(
      """return JSON.stringify({
        |  errors: window.viewer.errors,
        |  connectionState: window.viewer.pc.connectionState,
        |  stateTrail: window.states,
        |  channelState: window.viewer.channelState
        |})""".stripMargin)
      .map(json => JsonParser(json("result")("value").asString))
      .flatMap { state =>
        val healthy = state("errors").asVector.isEmpty &&
          state("connectionState").asString == "connected" &&
          state("stateTrail").asVector.isEmpty &&
          state("channelState").asString == "open"
        if (healthy) {
          logger.info("E2E: no pipeline error and the transport is intact after the soak")
        } else {
          Task.error[Unit](new RuntimeException(
            s"E2E: the resize broke the session during the soak: ${JsonFormatter.Compact(state)}"))
        }
      }
    marks <- viewer.eval(
      """return JSON.stringify({
        |  frames: window.viewer.frames,
        |  frameStamps: window.viewer.frameStamps,
        |  pongs: window.viewer.pongs
        |})""".stripMargin).map(json => JsonParser(json("result")("value").asString))
    _ <- waitFor(viewer, "video still advancing at the end of the soak", timeout = 8000) {
      viewer.eval("return window.viewer.frames").map(_("result")("value").asInt > marks("frames").asInt + 5)
    }
    _ <- waitFor(viewer, "server-to-client DataChannel writes still landing", timeout = 8000) {
      viewer.eval("return window.viewer.frameStamps").map(_("result")("value").asInt > marks("frameStamps").asInt)
    }
    _ <- waitFor(viewer, "DataChannel ping/pong still round-tripping", timeout = 8000) {
      viewer.eval("return window.viewer.pongs").map(_("result")("value").asInt > marks("pongs").asInt)
    }
    _ <- streamed.eval("document.title = 'idle'; return true;")
    _ <- viewer.eval(
      """window.viewer.dcSend({type: 'mousemove', x: 200, y: 140, buttons: 0});
        |window.viewer.dcSend({type: 'mousedown', x: 200, y: 140, button: 'left', buttons: 1, clickCount: 1, modifiers: 0});
        |window.viewer.dcSend({type: 'mouseup', x: 200, y: 140, button: 'left', buttons: 0, clickCount: 1, modifiers: 0});
        |return true;""".stripMargin)
    _ <- waitFor(viewer, "post-soak click routed through the DataChannel", timeout = 10000) {
      streamed.title.map(_ == "clicked")
    }
  } yield ()

  private def waitFor(browser: RoboBrowser, description: String,
                      timeout: Long = 30000)(check: Task[Boolean]): Task[Unit] = {
    val deadline = System.currentTimeMillis() + timeout
    def loop: Task[Unit] = check.flatMap {
      case true => logger.info(s"E2E: $description OK")
      case false if System.currentTimeMillis() > deadline =>
        Task.error(new RuntimeException(s"E2E: timed out waiting for $description"))
      case false => Task.sleep(250.millis).flatMap(_ => loop)
    }
    loop
  }
}
