package spec

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import robobrowser.scraper.DirectDownloader.Probe

/** The change-signal rules incremental crawling depends on. Getting these
  * wrong in the "skip" direction silently serves stale content, so each
  * rule is pinned, including the ones that must yield NO validator. */
class ProbeSpec extends AnyWordSpec with Matchers {
  "Probe.validator" should {
    "prefer a redirect's Location, the content-versioned CDN case" in {
      Probe(302, Some("https://cdn/x/123/a.pdf"), Some("W/\"e\""), None).validator shouldBe
        Some("location:https://cdn/x/123/a.pdf")
    }
    "use the ETag on a 2xx" in {
      Probe(200, None, Some("W/\"abc\""), Some("Tue, 01 Jan 2030 00:00:00 GMT")).validator shouldBe
        Some("etag:W/\"abc\"")
    }
    "fall back to Last-Modified when there is no ETag" in {
      Probe(200, None, None, Some("Tue, 01 Jan 2030 00:00:00 GMT")).validator shouldBe
        Some("modified:Tue, 01 Jan 2030 00:00:00 GMT")
    }
    "offer nothing when the server offers nothing (caller must fetch)" in {
      Probe(200, None, None, None).validator shouldBe None
    }
    "offer nothing for an error or a rejected HEAD, never a false 'unchanged'" in {
      Probe(404, None, Some("W/\"e\""), None).validator shouldBe None
      Probe(405, None, None, None).validator shouldBe None
      Probe(0, None, None, None).validator shouldBe None   // the network-failure sentinel
    }
    "offer nothing for a redirect without a Location" in {
      Probe(301, None, None, None).validator shouldBe None
    }
  }
}
