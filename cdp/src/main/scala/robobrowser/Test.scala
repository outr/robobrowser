package robobrowser

import fabric.io.JsonFormatter
import fabric.rw.Convertible
import rapid.{RapidApp, Task}
import robobrowser.select.Selector
import scribe.{rapid => logger}
import spice.http.client.HttpClient

import scala.concurrent.duration.DurationInt

object Test extends RapidApp {
  override def run(args: List[String]): Task[Unit] = for {
    _ <- logger.info("Creating RoboBrowser instance...")
    browser <- RoboBrowser()
    _ = browser.url.attach { url =>
      scribe.info(s"URL Changed: $url")
    }
    _ = browser.event.page.navigatedWithinDocument.attach { evt =>
      scribe.info(s"NavigatedWithinDocument: ${evt.url}")
    }
    _ <- logger.info("Navigating...")
    frame <- browser.navigate("https://www.newmexicopublicnotices.com")
    _ <- browser.waitForLoaded()
//    document <- browser.eval("document.getElementById('ctl00_ContentPlaceHolder1_as1_txtSearch').outerHTML")
//    _ <- logger.info(s"Doc: ${JsonFormatter.Default(document.json)}")
    selection = browser(Selector.Id("ctl00_ContentPlaceHolder1_as1_txtSearch"))
    count <- selection.count
    _ <- logger.info(s"Count? $count")
    _ <- selection.value("Hello, World!")
//    _ <- browser.eval("alert('Testing!');")
    value <- selection.value
    _ <- logger.info(s"Value: $value")
    _ <- logger.info("Waiting for detach...")
    _ <- browser.waitForDetach()
    _ <- logger.info("Detached! Disposing...")
    _ <- browser.dispose()
    _ <- HttpClient.dispose()
  } yield ()
}
