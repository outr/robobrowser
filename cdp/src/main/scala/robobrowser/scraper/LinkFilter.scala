package robobrowser.scraper

import spice.net.URL

trait LinkFilter {
  def evaluate(link: ScrapedLink): LinkAction
}

object LinkFilter {
  case class Domain(domain: String, action: LinkAction = LinkAction.Include) extends LinkFilter {
    override def evaluate(link: ScrapedLink): LinkAction = if (link.href.domain.equalsIgnoreCase(domain)) {
      action
    } else {
      LinkAction.Nothing
    }
  }

  def include(f: URL => Boolean): LinkFilter = (link: ScrapedLink) => if (f(link.href)) {
    LinkAction.Include
  } else {
    LinkAction.Nothing
  }

  def exclude(f: URL => Boolean): LinkFilter = (link: ScrapedLink) => if (f(link.href)) {
    LinkAction.Exclude
  } else {
    LinkAction.Nothing
  }
}