package robobrowser.scraper

import fabric.filter.SnakeToCamelFilter
import fabric.rw._
import rapid.{Task, logger}
import robobrowser.{DownloadState, RoboBrowser}
import spice.net.URL

import java.nio.file.Files
import scala.annotation.tailrec
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

case class RoboScraper(browser: RoboBrowser,
                       handler: ScrapeHandler,
                       filters: List[LinkFilter],
                       defaultAction: LinkAction = LinkAction.Exclude,
                       downloadConfig: Option[DownloadConfig] = None) {
  private var scraped = Set.empty[URL]
  @volatile private var downloadBehaviorConfigured = false
  downloadConfig.foreach(cfg => new DownloadInterceptor(browser, cfg))

  private def ensureDownloadBehavior: Task[Unit] = downloadConfig match {
    case Some(config) if !downloadBehaviorConfigured =>
      Task(Files.createDirectories(config.downloadDirectory)).flatMap { _ =>
        browser.configureDownloadPath(config.downloadDirectory)
      }.map { _ =>
        downloadBehaviorConfigured = true
      }
    case _ => Task.unit
  }

  private def guardAgainstDownloadResult(targetUrl: URL): Task[Unit] = Task {
    val navDownload = Option(browser.targetId)
      .flatMap(frameId => DownloadState.consumeFrameTrigger(frameId))
      .filter(_ == targetUrl)

    navDownload match {
      case Some(downloadUrl) =>
        throw DownloadState.DownloadTriggeredException(downloadUrl)
      case None =>
        if (DownloadState.wasRecentlyTriggered(targetUrl, includeHost = false)) {
          throw DownloadState.DownloadTriggeredException(targetUrl)
        }
    }
  }

  def scrape(urls: List[URL]): Task[Unit] = ensureDownloadBehavior.flatMap { _ =>
    var queue = urls

    def recurse: Task[Unit] = queue.headOption match {
      case Some(url) if scraped.contains(url) =>
        queue = queue.tail
        logger.debug(s"*** Skipping already processed: $url")
          .next(recurse)
      case Some(url) => for {
        _ <- Task {
          scraped = scraped + url
          queue = queue.tail
        }
        pageOpt <- handler.existing(url) match {
          case Some(page) => Task.pure(Some(page))
          case None => scrapePage(url).attempt.flatMap {
            case Success(page: ScrapedPage) => Task.pure[Option[ScrapedPage]](Option(page))
            case Failure(e: DownloadState.DownloadTriggeredException) =>
              logger.info(s"Download triggered for ${e.url} - skipping page scrape.")
                .next(Task.pure[Option[ScrapedPage]](None))
            case Failure(other: Throwable) => Task { throw other }
          }
        }
        _ <- pageOpt match {
          case Some(page) => for {
            _ <- handler.handle(page)
            _ <- Task {
              page.links.foreach { link =>
                if (isValidLink(link)) {
                  queue = link.href :: queue
                }
              }
            }
          } yield ()
          case None => Task.unit
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
    _ <- guardAgainstDownloadResult(url)
    page <- executeScrape()
  } yield page

  private def executeScrape(): Task[ScrapedPage] = for {
    result <- browser.executeScript("scrape_page.js")
    page <- result("result")("value").filterOne(SnakeToCamelFilter).as[ScrapedPage] match {
      case p if p.textAll.contains("Verifying you are human") => logger.warn("Encountered Captcha! Waiting and trying again...")
        .sleep(30.seconds)
        .next(executeScrape())
      case p => Task.pure(p)
    }
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