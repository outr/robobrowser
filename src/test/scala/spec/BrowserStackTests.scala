package spec

import com.outr.robobrowser.{Context, MobileBrowser}
import com.outr.robobrowser.integration.IntegrationTests
import com.outr.robobrowser.monitor.BrowserMonitor
import io.youi.net._
import org.openqa.selenium.By

import java.io.File
import scala.concurrent.duration._

case class BrowserStackTests(label: String, browser: MobileBrowser) extends IntegrationTests[MobileBrowser] {
  // TODO: Test on Android
  private var firstImage: String = _

  private lazy val monitor = new BrowserMonitor(browser)

  "Browser Stack Mobile File Uploading" when {
    "loading the file uploader" in {
      browser.load(url"https://the-internet.herokuapp.com/upload")
      browser.waitForLoaded()
    }
    "clicking the file upload button" in {
      // iOS
      browser.oneBy(By.id("file-upload")).click()

      // Android
//      browser.oneBy(By.xpath("//android.widget.Button[@resource-id=\"file-upload\"]"), Context.Native).click()
    }
    "opening the photo library" in {
      // iOS
      browser.oneBy(By.name("Photo Library"), Context.Native).click()

      browser.avoidStaleReference {
        browser.waitForResult(15.seconds) {
          browser.firstBy(By.name("All Photos"), Context.Native).orElse(
            browser.firstBy(By.name("Moments"), Context.Native)
          )
        }.click()
      }

      // Android
//      browser.oneBy(By.xpath("//android.widget.LinearLayout[@index=\"2\"]"), Context.Native).click()
    }
    "listing out images" in {
      // iOS
      val images = browser.waitForResult(15.seconds) {
        browser
          .by(By.xpath("//XCUIElementTypeImage | //XCUIElementTypeCell"), Context.Native)
          .map(_.attribute("name")) match {
            case Nil => None
            case list => Some(list)
          }
      }
      images shouldNot be(Nil)

      // Android
      /*firstImage = browser.waitForResult(30.seconds) {
        scribe.info("Waiting for result...")
        browser
          .by(By.xpath("//android.widget.FrameLayout[@clickable=\"true\"]"), Context.Native)
          .headOption
          .map(_.attribute("content-desc"))
      }*/
    }
    "selecting an image" in {
      // iOS
      browser
        .oneBy(By.xpath("//XCUIElementTypeImage[@index=0] | //XCUIElementTypeCell[@index=0]"), Context.Native)
        .click()

      // Android
      /*browser
        .oneBy(By.xpath(s"//android.widget.FrameLayout[@clickable=\"true\" and @content-desc=\"$firstImage\"]"), Context.Native)
        .click()*/
//      browser.debug(new File("debug"))
    }
    "choose the image" in {
      browser
        .oneBy(By.xpath("//XCUIElementTypeButton[@label=\"Done\"]"), Context.Native)
        .click()
      browser
        .oneBy(By.xpath("//XCUIElementTypeButton[@name=\"Choose\"]"), Context.Native)
        .click()
    }
    "click the upload button" in {
//      browser.pushFile("/data/local/tmp/LICENSE", new File("LICENSE"))
//      browser.oneBy(By.id("file-upload")).sendInput("/data/local/tmp/LICENSE")
      browser.sleep(2.seconds)
      browser.oneBy(By.id("file-submit")).click()
    }
    "verify the file uploaded" in {
      browser.waitFor(15.seconds) {
        browser.oneBy(By.cssSelector(".example > h3")).text == "File Uploaded!"
      } should be(true, s"File uploaded not detected!")
    }
  }

  // TODO: Add device-specific features for file upload support
  // def fileUpload(fileInput: By)(chooser: List[LocalImage] => List[LocalImage])
}