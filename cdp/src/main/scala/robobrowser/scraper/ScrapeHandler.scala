package robobrowser.scraper

import rapid.Task
import spice.net.URL

trait ScrapeHandler {
  def existing(url: URL): Option[ScrapedPage]

  def handle(page: ScrapedPage): Task[Unit]
}
