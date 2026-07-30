package spec

import fabric.io.JsonParser
import fabric.rw.Asable
import org.apache.tika.Tika
import rapid._
import robobrowser.scraper.{DownloadConfig, JsonWritingScrapeHandler, LinkFilter, RoboScraper, ScrapedPage}
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}
import spice.http.client.HttpClient
import spice.net._

import java.io.PrintWriter
import java.nio.file.{Files, Path}
import scala.io.Source
import scala.util.Try

object Test extends RapidApp {
  private val directory = Path.of("/data/scrape_test")
  private val files = directory.resolve("files")
  private lazy val tika = new Tika()

  override def run(args: List[String]): Task[Unit] = assignExtensions

  private def downloadFiles: Task[Unit] = rapid.Stream.listDirectory(directory)
    .filter(_.getFileName.toString.endsWith(".json"))
//    .filter(_.getFileName.toString == "www-enovationcontrols-com-products-powerview-u35-.json")
    .map(p => JsonParser(p).as[ScrapedPage])
    .flatMap { page =>
      rapid.Stream.emits(page.links)
    }
    .filter { link =>
      val fileName = link.href.path.parts.map(_.value).lastOption
      val extension = fileName.map { fn =>
        val index = fn.indexOf('.')
        if (index != -1) {
          fn.substring(index + 1).toLowerCase
        } else {
          ""
        }
      }
      val b = extension.exists(ext => Set("pdf", "docx", "xls", "xlsx", "txt").contains(ext))
      if (b) scribe.info(s"FileName: $fileName")
      b
    }
    .map { link =>
      val fileName = link.href.path.parts.last.value
      scribe.info(s"Downloading $fileName")
      val path = files.resolve(fileName)
      try {
        /*HttpClient
          .url(link.href)
          .send()
          .flatMap { response =>
          }*/
      } catch {
        case t: Throwable =>
          Files.deleteIfExists(path)
          scribe.warn(s"Failed to save $fileName: ${t.getMessage}")
      }
    }
    .drain

  private def writeURLLog: Task[Unit] = Task.defer {
    val writer = new PrintWriter("urls.txt")
    rapid.Stream.listDirectory(directory)
      .filter(_.getFileName.toString.endsWith(".json"))
      .map { path =>
        val page = JsonParser(path).as[ScrapedPage]
        writer.println(page.url.toString())
      }
      .drain
      .function {
        writer.flush()
        writer.close()
      }
  }

  private def assignExtensions: Task[Unit] = rapid.Stream.listDirectory(files)
    .foreach { path =>
      val fileName = path.getFileName.toString
      if (fileName.indexOf('.') == -1) {
        val mimeType = tika.detect(path)
        val contentType = ContentType.parse(mimeType).extension.get
        scribe.info(s"$fileName - $mimeType - $contentType")
        Files.move(path, path.getParent.resolve(s"$fileName.$contentType"))
      } else {
        // Already has file extension
      }
    }
    .drain

  private def scrape: Task[Unit] = RoboBrowser.withBrowser(
    config = RoboBrowserConfig(
      browserConfig = BrowserConfig(
        headless = false,
        useNewHeadlessMode = false
      ),
      tabSelector = TabSelector.AlwaysCreateNew
    )
  ) { browser =>
    for {
      _ <- logger.info("Scraping Starting...")
      handler = JsonWritingScrapeHandler(directory)
      scraper = RoboScraper(
        browser = browser,
        handler = handler,
        filters = List(
          LinkFilter.exclude { url =>
            url.path.toString().endsWith("/click") ||
              url.toString().contains("login-signin") ||
              url.toString().contains("auth/v2")
          },
          LinkFilter.Domain("enovationcontrols.com")
        ),
        downloadConfig = Some(DownloadConfig(
          directory = files
        ))
      )
//      _ <- scraper.scrape(List(url"https://www.enovationcontrols.com/products/openview-s70/"))
      urls <- Task {
        val source = Source.fromFile(Path.of("reprocess.urls").toFile)
        try {
          source.getLines().toList.map(URL.parse(_))
        } finally {
          source.close()
        }
      }
      _ = urls.foreach { url =>
        val path = handler.toPath(url)
        Files.deleteIfExists(path)
      }
      _ <- scraper.scrape(urls)
      _ <- logger.info("Scraping Complete!")
      _ <- browser.waitForDetach()
    } yield ()
  }

  override def result(result: Try[Unit]): Task[Int] = HttpClient.dispose().flatMap(_ => super.result(result))
}