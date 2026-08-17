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
 * so a portrait target streams portrait content: the session's stats carry the
 * target verbatim and it is never coerced back to the display's aspect. A
 * mid-session resize reconfigures that pipeline in place and emits no second
 * offer. How the target reaches the wire is the encoder branch's
 * [[ResizeBehavior]] — the frame becomes the target where the encoder is
 * re-pinned per resize, and the target is bordered into an unchanging canvas
 * where it is not — so the transmitted size is asserted per branch while the
 * render target is asserted the same way on both.
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
      val config = StreamConfig(width = Some(Portrait.width), height = Some(Portrait.height), maxFps = 30)
      val session = browser.stream.start(config).sync()
      try {
        session.connect(signals.add(_)).sync()

        session.renderSize shouldBe Portrait
        viewportSize shouldBe "390x844"
        mobileLayout shouldBe true

        val offers = awaitOffer(signals, atLeast = 1, timeoutMs = 60_000)
        offers should not be empty
        offers.head should include("m=video")
        offers.head should include("m=application")

        val behavior = session.resizeBehavior
        val canvas = PipelineBuilder.encodeCanvas(Display, config)

        val portraitStats = session.stats.sync()
        // The render target is what was asked for whichever branch is encoding,
        // and it keeps its own aspect rather than the display's
        portraitStats.renderSize shouldBe Portrait
        portraitStats.placement.content.height should be > portraitStats.placement.content.width
        portraitStats.encoder should not be empty
        // How that target is transmitted is the branch's business: the target
        // itself where the encoder is re-pinned per resize, the fixed canvas
        // (target bordered inside it) where it is not
        RenderSize(portraitStats.width, portraitStats.height) shouldBe (behavior match {
          case ResizeBehavior.Reconfigure => Portrait
          case ResizeBehavior.FixedCanvas => canvas
        })

        session.resize(Landscape.width, Landscape.height).sync()

        session.renderSize shouldBe Landscape
        viewportSize shouldBe "1280x820"
        mobileLayout shouldBe false

        // The resize reconfigures the live pipeline; webrtcbin, its DTLS
        // fingerprint and its ICE credentials are untouched, so the session
        // never offers a second time
        awaitOffer(signals, atLeast = 2, timeoutMs = 5_000) should have size 1

        val landscapeStats = session.stats.sync()
        landscapeStats.renderSize shouldBe Landscape
        behavior match {
          case ResizeBehavior.Reconfigure =>
            RenderSize(landscapeStats.width, landscapeStats.height) shouldBe Landscape
          case ResizeBehavior.FixedCanvas =>
            // The encoder was never asked to change, so the frame shape the
            // viewer negotiated is the frame shape it keeps receiving. There is
            // no re-pin for a per-resolution surface pool to accept silently and
            // then ignore, which is the whole point of the fixed canvas.
            RenderSize(landscapeStats.width, landscapeStats.height) shouldBe canvas
            landscapeStats.placement.bordered shouldBe true
        }
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
