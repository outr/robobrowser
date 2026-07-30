package spec

import fabric.io.JsonParser
import fabric.rw.Asable
import rapid.{RapidApp, Task, logger}
import robobrowser.scraper.ScrapedPage

import java.io.PrintWriter
import java.nio.file.Path

object Test4 extends RapidApp {
  override def run(args: List[String]): Task[Unit] = {
    val writer = new PrintWriter(Path.of("reprocess.urls").toFile)
    rapid.Stream.listDirectory(Path.of("scrape_test"))
      .filter(_.getFileName.toString.endsWith(".json"))
      .map { path =>
        val json = JsonParser(path)
        json.as[ScrapedPage]
      }
      .filter(_.textAll.contains("Verifying you are human"))
      .foreach { page =>
        writer.println(page.url.toString())
      }
      .drain
      .guarantee(Task {
        writer.flush()
        writer.close()
      })
  }
}
