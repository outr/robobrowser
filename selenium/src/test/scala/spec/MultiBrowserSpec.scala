package spec

import com.outr.robobrowser.browser.chrome.Chrome
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spice.net._

import scala.concurrent.duration.DurationInt
import scala.util.Try

class MultiBrowserSpec extends AnyWordSpec with Matchers {
  "Multi Browser" should {
    lazy val browser1 = Chrome.headless.create()
    lazy val browser2 = Chrome.headless.create()

    "initialize and load first browser" in {
      browser1.load(url"https://outr.com")
      browser1.title should be("")
    }
    "minimize the first browser" in {
      browser1.window.minimize()
    }
    "restore the browser" in {
      browser1.window.activate()
    }
    "initialize and load second browser" in {
      browser2.load(url"https://google.com")
      browser2.title should be("Google")
    }
    "dispose of both browsers" in {
      Try(browser1.dispose())
      Try(browser2.dispose())
    }
  }
}
