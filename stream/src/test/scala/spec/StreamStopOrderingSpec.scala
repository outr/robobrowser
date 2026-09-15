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
import scala.jdk.CollectionConverters.*

/**
 * Every caller of `stop()` waits for the pipeline to reach NULL, and `Bye`
 * reaches the signaling listener only after it has: a consumer that treats
 * either as "the session no longer holds the display" and disposes the
 * browser must never destroy Xvfb under a live capture.
 *
 * Self-skips (with the reason) when the host can't stream.
 */
class StreamStopOrderingSpec extends AnyWordSpec with Matchers {
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

  "stopping a stream session" should {
    "hold every caller until the pipeline is down, and send Bye only after it" in {
      skipReason.foreach(reason => cancel(s"Skipping live stop-ordering test: $reason"))
      val browser = RoboBrowser(RoboBrowserConfig(
        browserConfig = BrowserConfig(noSandbox = true, disableGPU = true, testType = true),
        tabSelector = TabSelector.FirstPage,
        virtualDisplay = Some(VirtualDisplayConfig(width = 1280, height = 720))
      )).sync()
      try {
        val session = browser.stream.start(StreamConfig(maxFps = 30, encoderOverride = sys.env.get("STREAM_ENCODER"))).sync()
        // Whether the session reported itself stopped at the moment each event happened.
        val seen = new ConcurrentLinkedQueue[(String, Boolean)]()
        session.connect { message =>
          if (message == SignalMessage.Bye) seen.add("bye" -> session.stopped())
        }.sync()
        Thread.sleep(1000L)

        val first = session.stop().map(_ => seen.add("first" -> session.stopped())).start()
        val second = session.stop().map(_ => seen.add("second" -> session.stopped())).start()
        first.join.sync()
        second.join.sync()

        val events = seen.asScala.toList
        withClue(s"events: $events: ") {
          events.map(_._1).toSet shouldBe Set("bye", "first", "second")
          events.foreach { case (_, stopped) => stopped shouldBe true }
        }
        browser.stream.sessions shouldBe empty
      } finally browser.dispose().sync()
    }
  }
}
