package spec

import com.outr.robobrowser.ReadyState

import java.io.File
import com.outr.robobrowser.chrome.RoboChrome
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.youi.net._

class RoboBrowserSpec extends AnyWordSpec with Matchers {
  "RoboBrowser" should {
    lazy val browser = new RoboChrome()
    lazy val screenshot = new File("screenshot.png")

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
    "dispose the browser" in {
      browser.dispose()
    }
  }
}