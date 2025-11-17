package robobrowser.scraper

import fabric.filter.SnakeToCamelFilter
import fabric.rw._
import rapid.{Task, logger}
import robobrowser.RoboBrowser
import spice.net.URL

import scala.annotation.tailrec

case class RoboScraper(browser: RoboBrowser,
                       handler: ScrapeHandler,
                       filters: List[LinkFilter],
                       defaultAction: LinkAction = LinkAction.Exclude) {
  private var scraped = Set.empty[URL]

  def scrape(url: URL): Task[Unit] = {
    var queue = List(url)

    def recurse: Task[Unit] = queue.headOption match {
      case Some(url) if scraped.contains(url) => recurse
      case Some(url) => for {
        _ <- Task {
          scraped = scraped + url
        }
        page <- scrapePage(url)
        _ <- handler.handle(page)
        _ = page.links.foreach { link =>
          if (isValidLink(link)) {
            queue = link.href :: queue
          }
        }
        _ <- recurse
      } yield ()
      case None => Task.unit
    }

    recurse
  }

  def scrapePage(url: URL): Task[ScrapedPage] = for {
    _ <- logger.info(s"Loading $url")
    _ <- browser.navigate(url.toString())
    _ <- browser.waitForLoaded()
    result <- browser.executeScript("scrape_page.js")
    page = result("result")("value").filterOne(SnakeToCamelFilter).as[ScrapedPage]
  } yield page

  def isValidLink(link: ScrapedLink): Boolean = {
    @tailrec
    def recurse(filters: List[LinkFilter]): LinkAction = filters.headOption match {
      case None => defaultAction
      case Some(filter) => filter.evaluate(link) match {
        case LinkAction.Include => LinkAction.Include
        case LinkAction.Exclude => LinkAction.Exclude
        case LinkAction.Nothing => recurse(filters.tail)
      }
    }

    recurse(filters) match {
      case LinkAction.Include => true
      case LinkAction.Exclude => false
      case LinkAction.Nothing => throw new RuntimeException("Final action must not be Nothing")
    }
  }
}