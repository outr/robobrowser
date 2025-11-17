package robobrowser.scraper

import fabric.io.JsonFormatter
import fabric.rw.Convertible
import rapid.Task
import spice.net.URL

import java.nio.file.{Files, Path}

case class JsonWritingScrapeHandler(directory: Path,
                                    url2Name: URL => String = JsonWritingScrapeHandler.urlToFileName) extends ScrapeHandler {
  override def shouldScrape(url: URL): Boolean = {
    val path = directory.resolve(url2Name(url))
    val exists = Files.exists(path)
    if (exists) {
      scribe.info(s"Already scraped! $path")
    }
    !exists
  }

  override def handle(page: ScrapedPage): Task[Unit] = Task {
    val path = directory.resolve(url2Name(page.url))
    scribe.info(s"Writing ${page.url} to $path...")
    Files.createDirectories(directory)
    val jsonString = JsonFormatter.Default(page.json)
    Files.writeString(path, jsonString)
  }
}

object JsonWritingScrapeHandler {
  def urlToFileName(url: URL): String = {
    val name = url.toString
      .stripPrefix("https://")
      .stripPrefix("http://")
      .replace(".", "-")
      .replace("/", "-")
      .replace("?", "-")
      .replace("=", "-")
    s"$name.json"
  }
}