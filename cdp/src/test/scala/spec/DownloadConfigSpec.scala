package spec

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import robobrowser.event.NetworkResponse
import robobrowser.scraper.{DownloadConfig, DownloadContext}
import spice.net.URL

class DownloadConfigSpec extends AnyWordSpec with Matchers {
  "DownloadConfig.defaultShouldDownload" should {
    "flag PDF mime types served as documents" in {
      val ctx = context(
        mimeType = "application/pdf",
        resourceType = "Document"
      )
      DownloadConfig.defaultShouldDownload(ctx) shouldBe true
    }

    "ignore standard HTML payloads" in {
      val ctx = context(
        mimeType = "text/html",
        resourceType = "Document"
      )
      DownloadConfig.defaultShouldDownload(ctx) shouldBe false
    }
  }

  "DownloadConfig.defaultFileName" should {
    "honor content disposition headers" in {
      val ctx = context(
        mimeType = "application/pdf",
        resourceType = "Document",
        headers = Map("Content-Disposition" -> """attachment; filename="manual.pdf"""")
      )

      DownloadConfig.defaultFileName(ctx) shouldBe "manual.pdf"
    }

    "append an extension when the filename omits one" in {
      val ctx = context(
        mimeType = "application/pdf",
        resourceType = "Document",
        headers = Map("Content-Disposition" -> """attachment; filename="manual"""" )
      )

      DownloadConfig.defaultFileName(ctx) shouldBe "manual.pdf"
    }
  }

  private def context(mimeType: String,
                      resourceType: String,
                      headers: Map[String, String] = Map.empty): DownloadContext = {
    val response = NetworkResponse(
      url = "https://example.com/resource",
      status = 200,
      statusText = "OK",
      headers = headers,
      mimeType = mimeType,
      charset = "utf-8",
      connectionReused = false,
      connectionId = 1,
      fromDiskCache = false,
      fromServiceWorker = false,
      fromPrefetchCache = false,
      encodedDataLength = 0L,
      protocol = "h2",
      alternateProtocolUsage = None,
      securityState = "secure"
    )

    DownloadContext(
      url = URL.parse("https://example.com/resource"),
      resourceType = resourceType,
      response = response
    )
  }
}



