package spec

import cats.effect.unsafe.implicits.global
import com.outr.robobrowser._
import com.outr.robobrowser.browser.chrome.Chrome
import com.outr.robobrowser.event.{Event, EventManager}
import fabric._

import java.io.File
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spice.http.server.MutableHttpServer
import spice.http.server.config.HttpServerListener
import spice.net._

import scala.concurrent.duration.DurationInt

class RoboBrowserSpec extends AnyWordSpec with Matchers {
  "RoboBrowser" should {
    lazy val browser = Chrome.headless.windowSize(1600, 1200).create()
    lazy val screenshot = new File("screenshot.png")
    lazy val server = new MutableHttpServer {
      config.clearListeners().addListeners(HttpServerListener(port = 8888))
      start().unsafeRunSync()
    }
    lazy val eventManager = new EventManager(browser, Some(server))

    "load Google" in {
      browser.load(url"https://google.com")
      browser.url should be(url"https://www.google.com")
      browser.title should be("Google")
      browser.readyState should be(ReadyState.Complete)
    }
    "do a Google search" in {
      val input = browser.oneBy(By.css("[name=\"q\"]"))
      input.tagName should be("input")
      input.sendKeys("robobrowser")
      input.submit()
      browser.waitFor(5.seconds)(browser.title == "robobrowser - Google Search")
      browser.title should be("robobrowser - Google Search")
    }
    "create a screenshot" in {
      browser.screenshot(screenshot)
      screenshot.length() should be > 0L
    }
//    "verify logs are working" in {
//      browser.logs.info("This is a test")
//      browser.logs().map(_.copy(timestamp = 0L)) should be(List(LogEntry(LogLevel.Info, 0L, "This is a test")))
//    }
    "monitor key event" in {
      val queue = eventManager.queue[Json]("test1")
      var received = List.empty[Event[Json]]

      val input = browser.oneBy(By.css("[name=\"q\"][type=\"text\"]"))
      queue.listen { evt =>
        received = evt :: received
      }
      queue.enqueue(obj("hello" -> "world!"), Some(input))
      browser.waitFor(5.seconds, 100.millis, blocking = false) {
        received.length == 1
      } should be(true)
      val event = received.head
      event.key should be("test1")
      event.value should be(obj("hello" -> "world!"))
      event.element.get.attribute("name") should be("q")

      val keyQueue = eventManager.queue.key.up("test2", None)
      var keyCounter = 0
      keyQueue.listen { evt =>
        scribe.info(s"Key Event: ${evt.value} / ${evt.element.map(_.tagName)}")
        if (evt.value.key != "Shift") {
          keyCounter += 1
        }
      }
      input.sendKeys("Testing")
      browser.waitFor(2.seconds, 100.millis, blocking = false) {
        keyCounter == 7
      } should be(true)
    }
    "dispose the browser" in {
      eventManager.dispose()
      browser.dispose()
    }
  }
}