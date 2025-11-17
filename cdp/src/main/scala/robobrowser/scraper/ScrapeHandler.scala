package robobrowser.scraper

import rapid.Task
import spice.net.URL

trait ScrapeHandler {
  def shouldScrape(url: URL): Boolean

  def handle(page: ScrapedPage): Task[Unit]
}
