package robobrowser.scraper

import rapid.Task

trait ScrapeHandler {
  def handle(page: ScrapedPage): Task[Unit]
}
