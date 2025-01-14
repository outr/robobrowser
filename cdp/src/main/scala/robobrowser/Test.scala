package robobrowser

import rapid.{RapidApp, Task}
import scribe.{rapid => logger}
import spice.http.client.HttpClient

import scala.util.Try

object Test extends RapidApp {
  override def run(args: List[String]): Task[Unit] = RoboBrowser.withBrowser(
    config = RoboBrowserConfig(
      browserConfig = BrowserConfig(
        headless = true
      )
    )
  ) { browser =>
    for {
      _ <- browser.navigate("https://outr.com")
      _ <- browser.waitForLoaded()
      _ <- logger.info("Loaded!")
    } yield ()
  }

  override def result(result: Try[Unit]): Task[Unit] = HttpClient.dispose().flatMap(_ => super.result(result))
}
