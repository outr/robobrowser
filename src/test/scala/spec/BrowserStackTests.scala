package spec

import com.outr.robobrowser.{Context, MobileBrowser}
import com.outr.robobrowser.integration.IntegrationTests
import com.outr.robobrowser.monitor.BrowserMonitor
import io.youi.net._
import org.openqa.selenium.By

import java.io.File
import scala.concurrent.duration._

case class BrowserStackTests(label: String, browser: MobileBrowser) extends IntegrationTests[MobileBrowser] {
  private lazy val monitor = new BrowserMonitor(browser)

  // TODO: Test on Android
  private var firstImage: String = _

  "Browser Stack Mobile File Uploading" when {
    "loading the file uploader" in {
      browser.load(url"https://the-internet.herokuapp.com/upload")
      browser.waitForLoaded()
    }
    "clicking the file upload button" in {
      // iOS
//      browser.oneBy(By.id("file-upload")).click()

      // Android
//      browser.oneBy(By.xpath("//android.widget.Button[@resource-id=\"file-upload\"]"), Context.Native).click()
    }
    "opening the photo library" in {
      // iOS
//      browser.oneBy(By.name("Photo Library"), Context.Native).click()

      // Android
//      browser.oneBy(By.xpath("//android.widget.LinearLayout[@index=\"2\"]"), Context.Native).click()
    }
    "listing out images" in {
      // iOS
      /*val images = browser.waitForResult(15.seconds) {
        browser
          .by(By.xpath("//XCUIElementTypeImage"), Context.Native)
          .map(_.attribute("name")) match {
            case Nil => None
            case list => Some(list)
          }
      }
      images should contain(
        "Video, nine seconds, January 05, 2018, 1:36 PM",
        "Video, eight seconds, November 22, 2017, 6:32 AM",
        "Video, thirteen seconds, September 14, 2017, 10:13 AM"
      )*/

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
//      browser
//        .oneBy(By.xpath("//XCUIElementTypeImage[@index=0]"), Context.Native)
//        .click()

      // Android
      /*browser
        .oneBy(By.xpath(s"//android.widget.FrameLayout[@clickable=\"true\" and @content-desc=\"$firstImage\"]"), Context.Native)
        .click()*/
//      browser.debug(new File("debug"))
    }
    /*"choose the image" in {
      monitor.refreshAndPause()
      browser
        .oneBy(By.xpath("//XCUIElementTypeButton[@name=\"Choose\"]"), Context.Native)
        .click()
    }*/
    "click the upload button" in {
      browser.pushFile("/data/local/tmp/LICENSE", new File("LICENSE"))
      browser.oneBy(By.id("file-upload")).sendInput("/data/local/tmp/LICENSE")
      browser.oneBy(By.id("file-submit")).click()
    }
    "verify the file uploaded" in {
      browser.oneBy(By.cssSelector(".example > h3")).text should be("File Uploaded!")
    }
  }

  // TODO: Add device-specific features for file upload support
  // def fileUpload(fileInput: By)(chooser: List[LocalImage] => List[LocalImage])
}