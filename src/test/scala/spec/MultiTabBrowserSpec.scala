package spec

import com.outr.robobrowser.{By, ReadyState, WindowHandle}
import com.outr.robobrowser.browser.chrome.Chrome
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spice.net._

import scala.concurrent.duration._

class MultiTabBrowserSpec extends AnyWordSpec with Matchers {
  "Multi-tab Browser" should {
    lazy val browser = Chrome.headless.create()

    var googleTab: Option[WindowHandle] = None
    var duckDuckGoTab: Option[WindowHandle] = None

    "initialize and load the first tab" in {
      browser.load(url"https://google.com")
      browser.url should be(url"https://www.google.com")
      browser.title should be("Google")
      browser.readyState should be(ReadyState.Complete)
    }
    "create a new tab" in {
      googleTab = Some(browser.window.handle) // Get a reference to the current tab
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
      val input = browser.oneBy(By.css("#search_form_input_homepage"))
      input.tagName should be("input")
      input.sendKeys("robobrowser")
      input.submit()
      browser.waitFor(5.seconds)(browser.title == "robobrowser at DuckDuckGo")
      browser.title should be("robobrowser at DuckDuckGo")
    }
    "switch back to Google tab" in {
      browser.window.handles.size should be(2)
      browser.window.switchTo(googleTab.getOrElse(fail()))
      browser.title should be("Google")
    }
    "dispose the browser" in {
      browser.dispose()
    }
  }
}
