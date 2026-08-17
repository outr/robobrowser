package spec

import fabric.Str
import fabric.io.{JsonFormatter, JsonParser}
import fabric.rw._
import rapid._
import robobrowser.display.VirtualDisplayConfig
import robobrowser.stream.Stream.stream
import robobrowser.stream.{RenderPlacement, RenderSize, StreamSession}
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}

import scala.concurrent.duration.DurationInt

/** Automated end-to-end acceptance (M2+M3+M6): a virtual-display browser is
  * streamed via the demo server; a second, headless RoboBrowser acts as the
  * viewer. Asserts: WebRTC connects, video frames advance, a DataChannel click
  * lands on a page element in the streamed browser, typed keys arrive in its
  * focused input, a run of mid-stream resizes each reaches the viewer as the
  * frame and the render target the session claims, all of them keeping the very
  * same peer connection alive (no second offer, no state change) and keeping it
  * alive under a soak long enough for a disturbed transport to surface, and the
  * latency harness reports a plausible value.
  * Run: sbt "stream/Test/runMain spec.StreamE2ETest" */
object StreamE2ETest extends RapidApp {
  private val ResizeWidth = 960
  private val ResizeHeight = 600

  /** Shrink, aspect flip, then grow back — the shape a pane being dragged
    * through several layouts produces, and the shape a per-resolution encoder
    * surface pool is most likely to mishandle. */
  private val ResizeSequence = List(
    RenderSize(ResizeWidth, ResizeHeight),
    RenderSize(640, 720),
    RenderSize(1180, 720),
    RenderSize(ResizeWidth, ResizeHeight)
  )

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
            // dcSend drops silently until the peer's SCTP association is up, and
            // nothing resends, so a click issued before then is simply lost
            _ <- waitFor(viewer, "input DataChannel open") {
              viewer.eval("return window.viewer.channelState").map(_("result")("value").asString == "open")
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
    _ <- ResizeSequence.foldLeft(Task.unit)((previous, size) =>
      previous.flatMap(_ => resizeAndVerify(session, viewer, size)))
    _ <- waitFor(viewer, "frames advancing past the resize") {
      viewer.eval("return window.viewer.frames").map(_("result")("value").asInt > before + 10)
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

  /** One resize, checked against the two things a viewer can actually observe.
    *
    * The frame it decodes is the size the session says it transmits — accounting
    * alone cannot see this, because the crop, the caps and the stats all follow
    * the request whether or not the bitstream does. And the render target it was
    * told about is the one that was requested, which on a branch that holds its
    * encode canvas fixed is the only thing that moves at all. Where the two
    * differ there is border, and the border is really black while the content
    * region really carries the page. */
  private def resizeAndVerify(session: StreamSession, viewer: RoboBrowser, size: RenderSize): Task[Unit] = for {
    _ <- session.resize(size.width, size.height)
    stats <- session.stats
    transmitted = s"${stats.width}x${stats.height}"
    _ <- if (stats.renderSize == size) Task.unit else Task.error[Unit](new RuntimeException(
      s"E2E: asked for $size but the session reports a render target of ${stats.renderSize}"))
    _ <- waitFor(viewer, s"viewer receiving $transmitted after a resize to $size", timeout = 15000) {
      viewer.eval("return window.viewer.videoSize").map(_("result")("value").asString == transmitted)
    }.handleError { t =>
      viewer.eval("return window.viewer.videoSize")
        .map(_("result")("value").asString)
        .flatMap(received => logger.error(
          s"E2E: resized to $size — the session transmits $transmitted, the viewer still receives $received"))
        .flatMap(_ => Task.error[Unit](t))
    }
    _ <- waitFor(viewer, s"viewer told the render target moved to $size", timeout = 10000) {
      viewer.eval("return window.viewer.renderSize").map(_("result")("value").asString == size.toString)
    }
    _ <- verifyBorder(viewer, session.placement)
  } yield ()

  /** A bordered frame really is bordered: black where the canvas exceeds the
    * render target, page pixels inside the content region the session named. */
  private def verifyBorder(viewer: RoboBrowser, placement: RenderPlacement): Task[Unit] =
    if (!placement.bordered) {
      logger.info(s"E2E: $placement fills the transmitted frame, no border to check")
    } else {
      val (borderX, borderY) =
        if (placement.offsetX > 0) (placement.offsetX / 2, placement.encoded.height / 2)
        else (placement.encoded.width / 2, placement.offsetY / 2)
      // Three quarters in: past the page's button, input and timestamp, so the
      // content sample is the page's own background rather than an element
      val contentX = placement.offsetX + placement.content.width * 3 / 4
      val contentY = placement.offsetY + placement.content.height * 3 / 4
      waitFor(viewer, s"black border at $borderX,$borderY and page pixels at $contentX,$contentY in $placement") {
        viewer.eval(
          s"""const border = window.viewer.sample($borderX, $borderY);
             |const content = window.viewer.sample($contentX, $contentY);
             |if (border === null || content === null) return false;
             |return Math.max(...border) < 48 && Math.min(...content) > 160;""".stripMargin)
          .map(_("result")("value").asBoolean)
      }
    }

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
