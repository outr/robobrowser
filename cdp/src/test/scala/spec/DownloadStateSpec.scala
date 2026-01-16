package spec

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import robobrowser.DownloadState
import spice.net.URL

class DownloadStateSpec extends AnyWordSpec with Matchers {
  "DownloadState.consumeFrameTrigger" should {
    "return the download url once and clear subsequent lookups" in {
      val url = URL.parse("https://spec-only.example.com/sample.pdf")
      val frameId = "frame-spec-download-state"

      DownloadState.mark(url, frameId = Some(frameId))

      DownloadState.consumeFrameTrigger(frameId) shouldBe Some(url)
      DownloadState.consumeFrameTrigger(frameId) shouldBe None
    }
  }
}




