package robobrowser.stream

import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import rapid.*
import robobrowser.display.{DisplayResizeUnsupportedException, VirtualDisplayConfig}
import robobrowser.stream.Stream.stream
import robobrowser.stream.gst.GstEngine
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

/**
 * Per-stream render targets and live resize, against a real headful Chrome on a
 * real Xvfb display with a real encoder.
 *
 * The target is the size the page lays out at and the exact rectangle captured,
 * so a portrait target streams portrait video: the SDP's negotiated resolution
 * and the session's stats both carry it, and neither is coerced back to the
 * display's aspect. A mid-session resize rebuilds the pipeline and emits a fresh
 * offer on the same signaling channel.
 *
 * Self-skips (with the reason) when the host can't stream.
 */
class StreamRenderTargetSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private val Portrait = RenderSize(390, 844)
  private val Landscape = RenderSize(1280, 820)
  private val Display = RenderSize(1920, 1080)

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
    "data:text/html,<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
      "<style>html,body{margin:0}#a{width:100vw;height:100vh;background:%23ff0000}" +
      "@media (max-width:500px){#a{background:%2300ff00}}</style></head>" +
      "<body><div id='a'></div></body></html>"

  private var browser: RoboBrowser = scala.compiletime.uninitialized

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    if (skipReason.isEmpty) {
      browser = RoboBrowser(RoboBrowserConfig(
        browserConfig = BrowserConfig(noSandbox = true, disableGPU = true, testType = true),
        tabSelector = TabSelector.FirstPage,
        virtualDisplay = Some(VirtualDisplayConfig(width = Display.width, height = Display.height))
      )).sync()
      browser.navigate(page).sync()
      browser.waitForLoaded().sync()
    }
  }

  override protected def afterAll(): Unit = {
    if (browser != null) browser.dispose().sync()
    super.afterAll()
  }

  /** The `a=imageattr` / resolution the offer advertises isn't in the SDP for
    * H.264 (resolution is carried in-band), so the negotiated size is read from
    * the session's own accounting, which is what the encoder was configured
    * with. The SDP is asserted structurally. */
  private def awaitOffer(offers: ConcurrentLinkedQueue[SignalMessage], atLeast: Int, timeoutMs: Long): List[String] = {
    val deadline = System.currentTimeMillis() + timeoutMs
    def collected: List[String] = offers.iterator().asScala.collect {
      case SignalMessage.Offer(sdp) => sdp
    }.toList
    while (collected.size < atLeast && System.currentTimeMillis() < deadline) Thread.sleep(100)
    collected
  }

  private def viewportSize: String =
    browser.eval("return window.innerWidth + 'x' + window.innerHeight").map(_("result")("value").asString).sync()

  private def mobileLayout: Boolean =
    browser.eval("return window.matchMedia('(max-width: 500px)').matches")
      .map(_("result")("value").asBoolean).sync()

  "A stream started with a portrait render target" should {

    "lay the page out at the target, capture it whole, and resize live" in {
      skipReason.foreach(reason => cancel(s"Skipping live render-target test: $reason"))

      val signals = new ConcurrentLinkedQueue[SignalMessage]()
      val session = browser.stream.start(StreamConfig(
        width = Some(Portrait.width),
        height = Some(Portrait.height),
        maxFps = 30
      )).sync()
      try {
        session.connect(signals.add(_)).sync()

        session.renderSize shouldBe Portrait
        viewportSize shouldBe "390x844"
        mobileLayout shouldBe true

        val offers = awaitOffer(signals, atLeast = 1, timeoutMs = 60_000)
        offers should not be empty
        offers.head should include("m=video")
        offers.head should include("m=application")

        val portraitStats = session.stats.sync()
        portraitStats.width shouldBe Portrait.width
        portraitStats.height shouldBe Portrait.height
        // The target's own aspect, not the display's — no letterbox padding
        portraitStats.height should be > portraitStats.width
        portraitStats.encoder should not be empty

        session.resize(Landscape.width, Landscape.height).sync()

        session.renderSize shouldBe Landscape
        viewportSize shouldBe "1280x820"
        mobileLayout shouldBe false

        val renegotiated = awaitOffer(signals, atLeast = 2, timeoutMs = 60_000)
        renegotiated should have size 2

        val landscapeStats = session.stats.sync()
        landscapeStats.width shouldBe Landscape.width
        landscapeStats.height shouldBe Landscape.height
      } finally {
        session.stop().sync()
      }
    }

    "refuse a target larger than the display it captures" in {
      skipReason.foreach(reason => cancel(s"Skipping live render-target test: $reason"))

      intercept[DisplayResizeUnsupportedException] {
        browser.stream.start(StreamConfig(
          width = Some(Display.width + 640),
          height = Some(Display.height + 360)
        )).sync()
      }
    }
  }
}
