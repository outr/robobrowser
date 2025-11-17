package robobrowser.scraper

import rapid.Task
import spice.net.URL

class MemoryScrapeHandler extends ScrapeHandler {
  private var _map = Map.empty[URL, ScrapedPage]

  def map: Map[URL, ScrapedPage] = _map

  override def handle(page: ScrapedPage): Task[Unit] = Task {
    _map += page.url -> page
  }
}
