package spec

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import rapid.*
import robobrowser.display.VirtualDisplayConfig
import robobrowser.stream.Stream.stream
import robobrowser.stream.gst.GstEngine
import robobrowser.stream.{SignalMessage, StreamConfig}
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

/**
 * Losing the X display under a live capture ends the stream, not the process.
 * Without the guard, Xlib's default exit handler calls `exit()` and this spec's
 * JVM dies mid-test (the fork reports the suite aborted).
 *
 * Self-skips (with the reason) when the host can't stream.
 */
class StreamDisplayLossSpec extends AnyWordSpec with Matchers {
  private val chromeAvailable: Boolean =
    List("/usr/bin/google-chrome", "/usr/bin/google-chrome-stable", "/usr/bin/chromium")
      .exists(p => new File(p).canExecute)
  private val xvfbAvailable: Boolean =
    sys.env.getOrElse("PATH", "").split(File.pathSeparatorChar).exists(dir => new File(dir, "Xvfb").canExecute)
  private val skipReason: Option[String] =
    if (!chromeAvailable) Some("Chrome/Chromium not installed")
    else if (!xvfbAvailable) Some("Xvfb not installed")
    else GstEngine.initResult match {
      case Left(error) => Some(s"GStreamer unavailable: $error")
      case Right(_) if GstEngine.missingElements.nonEmpty => Some(s"missing elements: ${GstEngine.missingElements.mkString(", ")}")
      case Right(_) if GstEngine.selectedEncoder.isEmpty => Some("no usable H.264 encoder")
      case Right(_) => None
    }

  "losing the display under a playing capture" should {
    "end the stream with an error and leave the process running" in {
      skipReason.foreach(reason => cancel(s"Skipping live display-loss test: $reason"))
      val browser = RoboBrowser(RoboBrowserConfig(
        browserConfig = BrowserConfig(noSandbox = true, disableGPU = true, testType = true),
        tabSelector = TabSelector.FirstPage,
        virtualDisplay = Some(VirtualDisplayConfig(width = 1280, height = 720))
      )).sync()
      val session = browser.stream.start(StreamConfig(maxFps = 30, encoderOverride = sys.env.get("STREAM_ENCODER"))).sync()
      val signals = new ConcurrentLinkedQueue[SignalMessage]()
      session.connect(message => signals.add(message)).sync()
      Thread.sleep(1500L)

      // Kill the display out from under the capture, as a crashed Xvfb would.
      browser.virtualDisplay.get.dispose().sync()
      Thread.sleep(3000L)

      // Still here: the process survived. The capture reported the loss.
      signals.asScala.exists(_.isInstanceOf[SignalMessage.Error]) shouldBe true
      session.stop().timeout(30.seconds).sync()
      session.stopped() shouldBe true
      scala.util.Try(browser.dispose().timeout(30.seconds).sync())
      succeed
    }
  }
}
