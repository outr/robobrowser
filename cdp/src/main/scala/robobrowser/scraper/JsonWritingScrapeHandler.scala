package robobrowser.scraper

import fabric.io.{JsonFormatter, JsonParser}
import fabric.rw.{Asable, Convertible}
import rapid.Task
import spice.net.{Parameters, URL}

import java.nio.file.{Files, Path}

case class JsonWritingScrapeHandler(directory: Path,
                                    url2Name: URL => String = JsonWritingScrapeHandler.urlToFileName) extends ScrapeHandler {
  def toPath(url: URL): Path = directory.resolve(url2Name(url))

  override def existing(url: URL): Option[ScrapedPage] = {
    val path = toPath(url)
    val exists = Files.exists(path)
    if (exists) {
      Some(JsonParser(path).as[ScrapedPage])
    } else {
      None
    }
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
    val base = url.copy(fragment = None).toString
      .stripPrefix("https://")
      .stripPrefix("http://")
      .replace(".", "-")
      .replace("/", "-")
      .replace("?", "-")
      .replace("=", "-")
    val maxBaseLength = 180
    val normalized = if (base.length <= maxBaseLength) {
      base
    } else {
      val hash = Integer.toHexString(url.toString.hashCode)
      val trimmedLength = math.max(32, maxBaseLength - hash.length - 1)
      s"${base.take(trimmedLength)}-$hash"
    }
    s"$normalized.json"
  }
}