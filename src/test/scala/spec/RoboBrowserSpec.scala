package spec

import com.outr.robobrowser.{Device, LogEntry, LogLevel, ReadyState, ScreenSize, WindowHandle}

import java.io.File
import com.outr.robobrowser.chrome.{ChromeOptions, RoboChrome}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.youi.net._

import scala.jdk.CollectionConverters._

class RoboBrowserSpec extends AnyWordSpec with Matchers {
  "RoboBrowser" should {
    lazy val browser = new RoboChrome()
    lazy val screenshot = new File("screenshot.png")

    var googleTab: Option[WindowHandle] = None
    var duckDuckGoTab: Option[WindowHandle] = None

    "load Google" in {
      browser.load(url"https://google.com")
      browser.url should be(url"https://www.google.com")
      browser.title should be("Google")
      browser.readyState should be(ReadyState.Complete)
    }
    "do a Google search" in {
      val input = browser.oneBy("[name=\"q\"]")
      input.tagName should be("input")
      input.sendInput("robobrowser")
      input.submit()
      browser.title should be("robobrowser - Google Search")
    }
    "create a screenshot" in {
      browser.screenshot(screenshot)
      screenshot.length() should be > 0L
    }
    "create a new tab" in {
      googleTab = Some(browser.window.handle)     // Get a reference to the current tab
      duckDuckGoTab = Some(browser.window.newTab())
      googleTab shouldNot be(duckDuckGoTab)
    }
    "load duckduckgo.com" in {
      browser.load(url"https://duckduckgo.com")
      browser.url should be(url"https://duckduckgo.com")
      browser.title should be("DuckDuckGo — Privacy, simplified.")
      browser.readyState should be(ReadyState.Complete)
    }
    "do a Duck Duck Go search" in {
      val input = browser.oneBy("#search_form_input_homepage")
      input.tagName should be("input")
      input.sendInput("robobrowser")
      input.submit()
      browser.title should be("robobrowser at DuckDuckGo")
    }
    "switch back to Google tab" in {
      browser.window.handles.size should be(2)
      browser.window.switchTo(googleTab.getOrElse(fail()))
      browser.title should be("robobrowser - Google Search")
    }
    "verify logs are working" in {
      browser.info("This is a test")
      browser.logs().map(_.copy(timestamp = 0L)) should be(List(LogEntry(LogLevel.Info, 0L, "This is a test")))
    }
    "dispose the browser" in {
      browser.dispose()
    }
  }
}