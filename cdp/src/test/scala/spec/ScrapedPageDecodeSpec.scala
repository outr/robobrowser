package spec

import fabric._
import fabric.rw._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import robobrowser.scraper.{RoboScraper, ScrapedPage}

/** The lenient decode guarantee: a bad href costs that link, never the page.
  * Both production failures live here as regressions — a #:~:text= fragment
  * ending in '.', and a root-anchored FQDN — alongside a genuinely
  * unparseable href that must be dropped rather than fail the decode. */
class ScrapedPageDecodeSpec extends AnyWordSpec with Matchers {
  private def link(href: String): Json = obj(
    "href" -> str(href), "text" -> str("x"), "title" -> Null, "rel" -> Null, "sameOrigin" -> bool(false)
  )

  private def page(links: Json*): Json = obj(
    "url" -> str("https://example.com/start"),
    "title" -> str("t"),
    "fetchedAt" -> str("now"),
    "textAll" -> str("body"),
    "links" -> arr(links*)
  )

  "dropUnparseableLinks" should {
    "keep every parseable link untouched" in {
      val json = page(link("https://example.com/a"), link("https://example.com/b"))
      val decoded = RoboScraper.dropUnparseableLinks(json, "https://example.com/start").as[ScrapedPage]
      decoded.links.map(_.href.toString) shouldBe List("https://example.com/a", "https://example.com/b")
    }
    "drop an unparseable href instead of failing the page decode" in {
      val json = page(link("https://example.com/a"), link(":not-a-url"), link("https://example.com/b"))
      val decoded = RoboScraper.dropUnparseableLinks(json, "https://example.com/start").as[ScrapedPage]
      decoded.links.map(_.href.toString) shouldBe List("https://example.com/a", "https://example.com/b")
    }
    "survive the production failure hrefs (text-fragment ending in '.', root-anchored FQDN)" in {
      val json = page(
        link("https://www.epa.gov/dera/learn#:~:text=diesel%20emissions."),
        link("http://www.sec.gov./"))
      val decoded = RoboScraper.dropUnparseableLinks(json, "https://example.com/start").as[ScrapedPage]
      decoded.links should have size 2
      decoded.links(1).href.host shouldBe "www.sec.gov"
    }
    "leave a page without links alone" in {
      val json = obj("url" -> str("https://example.com"), "title" -> str("t"),
        "fetchedAt" -> str("now"), "textAll" -> str("b"), "links" -> arr())
      RoboScraper.dropUnparseableLinks(json, "https://example.com").as[ScrapedPage].links shouldBe Nil
    }
  }
}
