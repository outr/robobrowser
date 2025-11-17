package robobrowser.scraper

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
}