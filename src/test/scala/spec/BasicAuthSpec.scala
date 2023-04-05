package spec

import com.outr.robobrowser.ReadyState
import com.outr.robobrowser.browser.chrome.Chrome
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spice.net.interpolation

class BasicAuthSpec extends AnyWordSpec with Matchers {
  "Basic Auth" should {
    lazy val browser = Chrome.windowSize(1600, 1200).create()

    "configure credentials" in {
      browser.setBasicAuth("admin", "admin")
    }
    "load basic auth page" in {
      browser.load(url"https://the-internet.herokuapp.com/basic_auth")
      browser.waitForLoaded()
//      browser.url should be(url"https://the-internet.herokuapp.com/basic_auth")
//      browser.title should be("Google")
//      browser.readyState should be(ReadyState.Complete)
    }
    "dispose the browser" in {
//      browser.dispose()
    }
  }
}
