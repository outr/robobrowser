package robobrowser.scraper

import fabric.rw._
import spice.net.URL

case class ScrapedLink(href: URL,
                       text: String,
                       title: Option[String],
                       rel: Option[String],
                       sameOrigin: Boolean)

object ScrapedLink {
  implicit val rw: RW[ScrapedLink] = RW.gen
}