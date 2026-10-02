package robobrowser.stream

import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import rapid.*
import robobrowser.display.VirtualDisplayConfig
import robobrowser.stream.Stream.stream
import robobrowser.stream.gst.GstEngine
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}
import scribe.Level
import scribe.handler.LogHandler
import spec.StreamDemoServer

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

/**
 * A viewer that goes away without a bye ends its session as the viewer leaving,
 * not as a pipeline failure: the session tears down within the disconnect grace,
 * the signaling listener gets a `Bye` and no `Error`, and nothing in the stream
 * module logs at ERROR. A failure while the viewer is still connected is still
 * an error.
 *
 * The viewer's signaling socket stays open throughout, so only the WebRTC
 * transport can tell the session the viewer left.
 *
 * Self-skips (with the reason) when the host can't stream.
 */
class StreamPeerGoneSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  private val Port = 8905
  private val ViewerUrl = s"http://localhost:$Port/"
  // Settling time past the grace: the teardown itself and a late bus message.
  private val Margin = 5_000L

  private val chromeAvailable: Boolean =
    List("/usr/bin/google-chrome", "/usr/bin/google-chrome-stable", "/usr/bin/chromium",
      "/usr/local/bin/google-chrome", "/opt/google/chrome/chrome")
      .exists(p => new File(p).canExecute)

  private val xvfbAvailable: Boolean =
    sys.env.getOrElse("PATH", "").split(File.pathSeparatorChar)
      .exists(dir => new File(dir, "Xvfb").canExecute)

  private val skipReason: Option[String] =
    if (!chromeAvailable) Some("Chrome/Chromium not installed")
    else if (!xvfbAvailable) Some("Xvfb not installed")
    else GstEngine.initResult match {
      case Left(error) => Some(s"GStreamer unavailable: $error")
      case Right(_) if GstEngine.missingElements.nonEmpty => Some(s"missing elements: ${GstEngine.missingElements.mkString(", ")}")
      case Right(_) if GstEngine.selectedEncoder.isEmpty => Some("no usable H.264 encoder")
      case Right(_) => None
    }

  private val page: String =
    "data:text/html,<html><body style='margin:0;background:%23202030'>" +
      "<div id='t' style='color:%23fff;font-size:40px'>peer gone</div>" +
      "<script>setInterval(() => document.getElementById('t').textContent = Date.now(), 50)</script>" +
      "</body></html>"

  // What the stream module logged, by level.
  private val logged = new ConcurrentLinkedQueue[(Level, String)]()
  private val capture = LogHandler(Level.Info) { record =>
    if (record.className.startsWith("robobrowser.stream")) logged.add(record.level -> record.logOutput.plainText)
  }

  private var streamed: RoboBrowser = scala.compiletime.uninitialized
  private var viewer: RoboBrowser = scala.compiletime.uninitialized
  private var server: StreamDemoServer = scala.compiletime.uninitialized

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    scribe.Logger.root.withHandler(capture).replace()
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
    scribe.Logger.root.withoutHandler(capture).replace()
    super.afterAll()
  }

  "A viewer that goes away without a bye" should {
    "end the session as peer gone when it closes its peer connection" in {
      leaveWithoutBye("window.viewer.pc.close()")
    }
    "end the session as peer gone when it closes its DataChannel" in {
      leaveWithoutBye("window.viewer.closeChannel()")
    }
  }

  "A pipeline error while the viewer is connected" should {
    "still be reported as an error" in {
      skipReason.foreach(reason => cancel(s"Skipping live peer-gone test: $reason"))
      val (session, signals) = connectViewer()
      try {
        session.failure("sctpenc0", "Could not write to resource.").sync()
        await("the error signal")(signals.asScala.collectFirst { case e: SignalMessage.Error => e })
        errorsLogged.exists(_.contains("Could not write to resource")) shouldBe true
        session.stopped() shouldBe false
        session.endReason shouldBe None
      } finally {
        session.stop().sync()
        disconnectViewer()
      }
    }
  }

  private def leaveWithoutBye(leave: String): Unit = {
    skipReason.foreach(reason => cancel(s"Skipping live peer-gone test: $reason"))
    val (session, signals) = connectViewer()
    try {
      val left = System.currentTimeMillis()
      viewer.eval(s"$leave; return true").sync()

      val bound = StreamSession.DisconnectGrace.toMillis + Margin
      await("the session to end", bound)(Some(true).filter(_ => session.stopped()))
      val took = System.currentTimeMillis() - left
      // A late bus message from the departed transport must not log an error either.
      Thread.sleep(2_000L)

      withClue(s"ended after ${took}ms, signals ${signals.asScala.toList}, errors $errorsLogged: ") {
        took should be <= bound
        session.endReason shouldBe Some(StreamEndReason.PeerGone)
        signals.asScala should contain(SignalMessage.Bye)
        signals.asScala.exists(_.isInstanceOf[SignalMessage.Error]) shouldBe false
        errorsLogged shouldBe empty
        logged.asScala.exists { case (level, text) => level == Level.Info && text.contains("viewer left") } shouldBe true
        streamed.stream.sessions shouldBe empty
      }
    } finally {
      session.stop().sync()
      disconnectViewer()
    }
  }

  /** Open the viewer page and wait until its peer is connected and the session's
    * capture stamps are arriving over the DataChannel. */
  private def connectViewer(): (StreamSession, ConcurrentLinkedQueue[SignalMessage]) = {
    logged.clear()
    viewer.navigate(ViewerUrl).sync()
    viewer.waitForLoaded().sync()
    val session = await("a session for the viewer")(streamed.stream.sessions.headOption).get
    val signals = new ConcurrentLinkedQueue[SignalMessage]()
    session.toClient.attach(signals.add(_))
    await("peer connected")(Some(viewerValue("pc.connectionState")).filter(_ == "connected"))
    await("data channel open")(Some(viewerValue("channelState")).filter(_ == "open"))
    await("capture stamps arriving")(Some(viewerValue("frameStamps")).filter(_.toInt >= 2))
    (session, signals)
  }

  private def disconnectViewer(): Unit = {
    viewer.navigate("about:blank").sync()
    viewer.waitForLoaded().sync()
    await("no sessions left")(Some(true).filter(_ => streamed.stream.sessions.isEmpty))
  }

  private def viewerValue(expression: String): String =
    viewer.eval(s"return window.viewer ? String(window.viewer.$expression) : ''")
      .map(_("result")("value").asString).sync()

  private def errorsLogged: List[String] = logged.asScala.collect {
    case (level, text) if level.value >= Level.Error.value => text
  }.toList

  private def await[T](description: String, timeoutMs: Long = 60_000L)(check: => Option[T]): Option[T] = {
    val deadline = System.currentTimeMillis() + timeoutMs
    var result = check
    while (result.isEmpty && System.currentTimeMillis() < deadline) {
      Thread.sleep(100L)
      result = check
    }
    if (result.isEmpty) fail(s"Timed out waiting for $description")
    result
  }
}
