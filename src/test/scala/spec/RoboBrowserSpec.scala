package spec

import com.outr.robobrowser.{Device, ReadyState, ScreenSize, WindowHandle}

import java.io.File
import com.outr.robobrowser.chrome.{ChromeOptions, RoboChrome}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.youi.net._

import scala.jdk.CollectionConverters._

class RoboBrowserSpec extends AnyWordSpec with Matchers {
  "RoboBrowser" should {
    lazy val browser = new RoboChrome(ChromeOptions(device = Device(screenSize = Some(ScreenSize())))) {
      override protected def logCapabilities: Boolean = true
    }
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
      val logs = browser.logs()
      logs.foreach { entry =>
        scribe.info(s"${entry.getLevel} - ${entry.getMessage}")
      }
      logs.length should be > 0
    }
    "test replacing console" in {
      browser.execute(
        """window.oc = window.console;
          |window.console = {};
          |window.logs = [];
          |
          |window.console.clear = function() {
          |  window.logs = [];
          |}
          |
          |window.console.log = function(message) {
          |  window.logs.push({'level': 'info', 'message': message});
          |  window.oc.log(message);
          |};
          |
          |""".stripMargin)
      browser.execute("console.log('Testing');")
      browser.execute("return window.logs;").asInstanceOf[java.util.List[java.util.Map[String, String]]].asScala.toList.foreach { map =>
        scribe.info("Entry:")
        map.asScala.foreach {
          case (key, value) => scribe.info(s"  $key = $value")
        }
      }
    }
    "dispose the browser" in {
      browser.dispose()
    }
  }
}