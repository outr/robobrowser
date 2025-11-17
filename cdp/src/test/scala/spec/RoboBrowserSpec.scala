package spec

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import rapid.{AsyncTaskSpec, logger}
import robobrowser.input.Key
import robobrowser.select.Selector
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}

import java.io.File
import java.nio.file.{Files, Path}

class RoboBrowserSpec extends AsyncWordSpec with AsyncTaskSpec with Matchers {
  private var browser: RoboBrowser = _

  "RoboBrowser" should {
    lazy val screenshot = Path.of("screenshot.png")

    "create a new headless browser" in {
      RoboBrowser(config = RoboBrowserConfig(
        browserConfig = BrowserConfig(useNewHeadlessMode = false)
      )).map { browser =>
        this.browser = browser
        browser.url() should be("about:blank")
      }
    }
    "load outr.com" in {
      browser.navigate("https://outr.com").succeed
    }
    "wait for outr.com to load" in {
      browser.waitForLoaded().succeed
    }
    "verify the browser title is set for outr.com" in {
      browser.title.map(_ should be("OUTR Software, LLC | Expert Software Development for Web, Mobile, Desktop, and Server"))
    }
    "load Google" in {
      browser.navigate("https://google.com").succeed
    }
    "wait for Google to load" in {
      browser.waitForLoaded().succeed
    }
    "verify the browser title" in {
      browser.title.map(_ should be("Google"))
    }
    "do a Google search" in {
      val q = Selector("[name=\"q\"]")
      for {
        _ <- browser(q).focus
        _ <- browser.key.send(Key.text("robobrowser"))
        _ <- browser.key.`type`(Key.Enter)
        _ <- browser.waitForCondition(browser.title.map { title =>
          title == "robobrowser - Google Search"
        })
      } yield succeed
    }
    "create a screenshot" in {
      browser.screenshot(screenshot).map { _ =>
        Files.size(screenshot) should be > 0L
      }
    }
    "dispose of the browser" in {
      browser.dispose().succeed
    }
  }
}
