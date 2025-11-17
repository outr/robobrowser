package robobrowser.scraper

import fabric.rw._
import spice.net.URL

case class ScrapedPage(url: URL,
                       title: String,
                       fetchedAt: String,
                       textAll: String,
                       links: List[ScrapedLink])

object ScrapedPage {
  implicit val rw: RW[ScrapedPage] = RW.gen
}