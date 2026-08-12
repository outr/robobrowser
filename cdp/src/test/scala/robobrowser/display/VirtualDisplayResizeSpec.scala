package robobrowser.display

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import rapid.*

import java.io.File

/**
 * The virtual display's resize contract, against a real Xvfb.
 *
 * Xvfb builds RANDR and reports geometry through it, but advertises exactly one
 * mode — the framebuffer it was spawned with — and rejects `RRSetScreenSize`, so
 * a display cannot grow past its allocated size. That is why a stream's render
 * target is a capture region within the display rather than a display resize,
 * and why callers that need a bigger surface allocate it up front.
 *
 * Self-skips when Xvfb isn't installed.
 */
class VirtualDisplayResizeSpec extends AnyWordSpec with Matchers {

  private val xvfbAvailable: Boolean =
    sys.env.getOrElse("PATH", "").split(File.pathSeparatorChar)
      .exists(dir => new File(dir, "Xvfb").canExecute)

  private def withDisplay[A](width: Int, height: Int)(f: VirtualDisplay => A): A = {
    val display = DisplayAllocator.allocate(VirtualDisplayConfig(width = width, height = height)).sync()
    try f(display) finally display.dispose().sync()
  }

  "VirtualDisplay" should {

    "report the size it was allocated at, as the X server sees it" in {
      if (!xvfbAvailable) cancel("Xvfb not installed")
      withDisplay(1280, 720) { display =>
        display.width shouldBe 1280
        display.height shouldBe 720
        RandrResizer.dimensions(display.displayName) shouldBe Some((1280, 720))
      }
    }

    "treat a resize to the current size as a no-op" in {
      if (!xvfbAvailable) cancel("Xvfb not installed")
      withDisplay(1280, 720) { display =>
        display.resize(1280, 720).sync()
        display.width shouldBe 1280
        display.height shouldBe 720
      }
    }

    "raise an actionable failure when the server refuses to resize" in {
      if (!xvfbAvailable) cancel("Xvfb not installed")
      withDisplay(1280, 720) { display =>
        val thrown = intercept[DisplayResizeUnsupportedException] {
          display.resize(1920, 1080).sync()
        }
        thrown.currentWidth shouldBe 1280
        thrown.currentHeight shouldBe 720
        thrown.requestedWidth shouldBe 1920
        thrown.requestedHeight shouldBe 1080
        thrown.getMessage should include("VirtualDisplayConfig")
        // The failed attempt left the framebuffer exactly as it was
        RandrResizer.dimensions(display.displayName) shouldBe Some((1280, 720))
        display.width shouldBe 1280
      }
    }

    "reject a non-positive size" in {
      if (!xvfbAvailable) cancel("Xvfb not installed")
      withDisplay(1280, 720) { display =>
        intercept[IllegalArgumentException] {
          display.resize(0, 720).sync()
        }
      }
    }
  }
}
