package spec

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import robobrowser.scraper.DirectDownloader
import spice.net.URL

class DirectDownloaderSpec extends AnyWordSpec with Matchers {
  "fileNameFor" should {
    "prefer the Content-Disposition filename" in {
      DirectDownloader.fileNameFor(URL.parse("https://x.com/dl/abc123"),
        Some("""attachment; filename="Owner Manual.pdf"""")) shouldBe "Owner Manual.pdf"
    }
    "decode RFC 5987 encoded filenames" in {
      DirectDownloader.fileNameFor(URL.parse("https://x.com/dl"),
        Some("attachment; filename*=UTF-8''Spec%20Sheet.pdf")) shouldBe "Spec Sheet.pdf"
    }
    "fall back to the URL basename, query-stripped" in {
      DirectDownloader.fileNameFor(URL.parse("https://x.com/files/data-sheet.pdf?rev=3"), None) shouldBe "data-sheet.pdf"
    }
    "never return path separators" in {
      DirectDownloader.fileNameFor(URL.parse("https://x.com/a"),
        Some("""attachment; filename="../../etc/passwd"""")) should not include "/"
    }
  }

  "ensureExtension" should {
    "leave named files alone" in {
      DirectDownloader.ensureExtension("guide.pdf", Some("application/pdf"), Array.emptyByteArray) shouldBe "guide.pdf"
    }
    "assign from the response content type" in {
      DirectDownloader.ensureExtension("abc123", Some("application/pdf"), Array.emptyByteArray) shouldBe "abc123.pdf"
    }
    "detect via Tika when the header is useless" in {
      val pdfMagic = "%PDF-1.7\n".getBytes ++ Array.fill[Byte](64)(0)
      DirectDownloader.ensureExtension("abc123", None, pdfMagic) shouldBe "abc123.pdf"
    }
  }
}
