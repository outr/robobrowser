package spec

import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import rapid.*
import robobrowser.display.VirtualDisplayConfig
import robobrowser.stream.Stream.stream
import robobrowser.stream.gst.GstEngine
import robobrowser.stream.{StreamConfig, StreamSession}
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}

import java.io.File

/**
 * Stopping a session whose peer actually connected, repeatedly, in one JVM.
 *
 * A session that only ever emitted an offer tears down cleanly; one that
 * completed ICE and DTLS and carried media exercises a much larger set of
 * native objects, and any of those released twice corrupts the process heap.
 * That corruption is silent at the point of the mistake — it surfaces as an
 * abort at some later allocation, typically while the next pipeline is being
 * built, so the assertions of a second cycle are the detector.
 *
 * Each cycle drives a real viewer to `connected` with frames advancing, reads
 * the session's stats the way a consumer polling a live stream does, stops the
 * session, and forces the binding's reference reaper to run so anything the
 * teardown left dangling is released here rather than at an arbitrary later
 * allocation. Three cycles run in one JVM; reaching the last one's assertions
 * is the proof.
 *
 * Self-skips (with the reason) when the host can't stream.
 */
class StreamTeardownSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  private val Cycles = 3
  private val Port = 8902
  private val ViewerUrl = s"http://localhost:$Port/"

  private val chromeAvailable: Boolean =
    List("/usr/bin/google-chrome", "/usr/bin/google-chrome-stable", "/usr/bin/chromium",
      "/usr/local/bin/google-chrome", "/opt/google/chrome/chrome")
      .exists(p => new File(p).canExecute)

  private val xvfbAvailable: Boolean =
    sys.env.getOrElse("PATH", "").split(File.pathSeparatorChar)
      .exists(dir => new File(dir, "Xvfb").canExecute)

  private val gstReason: Option[String] = GstEngine.initResult match {
    case Left(error) => Some(s"GStreamer unavailable: $error")
    case Right(_) =>
      if (GstEngine.missingElements.nonEmpty) Some(s"missing GStreamer elements: ${GstEngine.missingElements.mkString(", ")}")
      else if (GstEngine.selectedEncoder.isEmpty) Some("no usable H.264 encoder")
      else None
  }

  private val skipReason: Option[String] =
    if (!chromeAvailable) Some("Chrome/Chromium not installed")
    else if (!xvfbAvailable) Some("Xvfb not installed")
    else gstReason

  private val page: String =
    "data:text/html,<html><body style='margin:0;background:%23202030'>" +
      "<div id='t' style='color:%23fff;font-size:40px'>teardown</div>" +
      "<script>setInterval(() => document.getElementById('t').textContent = Date.now(), 50)</script>" +
      "</body></html>"

  private var streamed: RoboBrowser = scala.compiletime.uninitialized
  private var viewer: RoboBrowser = scala.compiletime.uninitialized
  private var server: StreamDemoServer = scala.compiletime.uninitialized

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    if (skipReason.isEmpty) {
      streamed = RoboBrowser(RoboBrowserConfig(
        browserConfig = BrowserConfig(noSandbox = true, disableGPU = true, testType = true),
        tabSelector = TabSelector.FirstPage,
        virtualDisplay = Some(VirtualDisplayConfig(width = 1280, height = 720))
      )).sync()
      streamed.navigate(page).sync()
      streamed.waitForLoaded().sync()
      server = new StreamDemoServer(streamed, port = Port,
        streamConfig = StreamConfig(maxFps = 30, encoderOverride = sys.env.get("STREAM_ENCODER")))
      server.start().sync()
      viewer = RoboBrowser(RoboBrowserConfig(
        browserConfig = BrowserConfig(noSandbox = true, disableGPU = true, testType = true),
        tabSelector = TabSelector.FirstPage
      )).sync()
    }
  }

  override protected def afterAll(): Unit = {
    if (viewer != null) viewer.dispose().sync()
    if (server != null) server.stop().sync()
    if (streamed != null) streamed.dispose().sync()
    super.afterAll()
  }

  "A stream session whose peer connected" should {
    "survive being stopped, repeatedly, with a fresh session built after each stop" in {
      skipReason.foreach(reason => cancel(s"Skipping live teardown test: $reason"))

      (1 to Cycles).foreach { cycle =>
        withClue(s"cycle $cycle: ") {
          // A fresh viewer page opens a fresh signaling socket, which builds a
          // fresh pipeline — the allocation the previous cycle's corruption
          // would abort in.
          viewer.navigate(ViewerUrl).sync()
          viewer.waitForLoaded().sync()

          val session = await("a session for the viewer")(streamed.stream.sessions.headOption).get
          await("peer connected") {
            Some(viewer.eval("return window.viewer ? window.viewer.pc.connectionState : ''")
              .map(_("result")("value").asString).sync()).filter(_ == "connected")
          }
          await("frames advancing") {
            Some(viewer.eval("return window.viewer.frames").map(_("result")("value").asInt).sync())
              .filter(_ > 10)
          }

          // Consumers poll a live session; the stats path touches native
          // objects that only exist once media is actually flowing.
          val stats = session.stats.sync()
          stats.width should be > 0
          stats.encoder should not be empty

          stopAndSettle(session)
          streamed.stream.sessions shouldBe empty
        }
      }
    }
  }

  /** Stop, drop the peer, and force the binding's reference reaper to run: a
    * native object released twice is freed here, at a known point, rather than
    * at whatever allocation happens to come next. */
  private def stopAndSettle(session: StreamSession): Unit = {
    session.stop().sync()
    viewer.navigate("about:blank").sync()
    viewer.waitForLoaded().sync()
    (1 to 3).foreach { _ =>
      System.gc()
      Thread.sleep(250L)
    }
  }

  private def await[T](description: String, timeoutMs: Long = 60_000L)(check: => Option[T]): Option[T] = {
    val deadline = System.currentTimeMillis() + timeoutMs
    var result = check
    while (result.isEmpty && System.currentTimeMillis() < deadline) {
      Thread.sleep(250L)
      result = check
    }
    if (result.isEmpty) fail(s"Timed out waiting for $description")
    result
  }
}
