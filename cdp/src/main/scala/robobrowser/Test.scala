package robobrowser

import rapid.{RapidApp, Task}
import scribe.{rapid => logger}
import spice.http.client.HttpClient

object Test extends RapidApp {
  override def run(args: List[String]): Task[Unit] = for {
    _ <- logger.info("Creating RoboBrowser instance...")
    browser <- RoboBrowser()
    _ <- logger.info("Navigating to outr.com...")
    frame <- browser.navigate("https://outr.com")
    _ <- logger.info("Waiting for detach...")
    _ <- browser.waitForDetach()
    _ <- logger.info("Detached! Disposing...")
    _ <- browser.dispose()
    _ <- HttpClient.dispose()
  } yield ()
}
