package robobrowser

import rapid._
import spice.http.client.HttpClient

import scala.util.Try

object Test extends RapidApp {
  override def run(args: List[String]): Task[Unit] = RoboBrowser.withBrowser(
    config = RoboBrowserConfig(
      browserConfig = BrowserConfig(
        headless = false
      )
    )
  ) { browser =>
    for {
      _ <- browser.navigate("https://outr.com")
      _ <- browser.waitForLoaded()
      _ <- logger.info("Loaded!")
      _ <- browser.waitForDetach()
    } yield ()
  }

  override def result(result: Try[Unit]): Task[Unit] = HttpClient.dispose().flatMap(_ => super.result(result))
}
