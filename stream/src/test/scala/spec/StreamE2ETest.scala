package spec

import fabric.Str
import fabric.io.JsonFormatter
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
  * focused input, and the latency harness reports a plausible value.
  * Run: sbt "stream/Test/runMain spec.StreamE2ETest" */
object StreamE2ETest extends RapidApp {
  private val TestPage =
    "data:text/html,<html><body style='margin:0'>" +
      "<button id='b' style='position:absolute;left:100px;top:100px;width:200px;height:80px;font-size:30px' " +
      "onclick=\"document.title='clicked'; document.getElementById('i').focus()\">Click me</button>" +
      "<input id='i' style='position:absolute;left:100px;top:220px;width:200px;height:40px;font-size:20px'>" +
      "</body></html>"

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
