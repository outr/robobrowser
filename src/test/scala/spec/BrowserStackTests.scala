package spec

import com.outr.robobrowser.{Context, MobileBrowser}
import com.outr.robobrowser.integration.IntegrationTests
import com.outr.robobrowser.monitor.Monitor
import io.youi.net._
import org.openqa.selenium.By

import scala.concurrent.duration._

case class BrowserStackTests(label: String, browser: MobileBrowser) extends IntegrationTests[MobileBrowser] {
  private lazy val monitor = new Monitor(browser)

  "Browser Stack Mobile File Uploading" when {
    "loading the file uploader" in {
      browser.load(url"https://the-internet.herokuapp.com/upload")
    }
    "clicking the file upload button" in {
      browser.oneBy(By.id("file-upload")).click()
    }
    "opening the photo library" in {
      val photoLibrary = browser.oneBy(By.name("Photo Library"), Context.Native)
      photoLibrary.click()
    }
    "listing out images" in {
      val images = browser.waitForResult(15.seconds) {
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
      )
    }
    "selecting an image" in {
      browser
        .oneBy(By.xpath("//XCUIElementTypeImage[@index=0]"), Context.Native)
        .click()
    }
    "choose the image" in {
      browser
        .oneBy(By.xpath("//XCUIElementTypeButton[@name=\"Choose\"]"), Context.Native)
        .click()
    }
    "click the upload button" in {
      browser.oneBy(By.id("file-submit")).click()
    }
    "verify the file uploaded" in {
      browser.oneBy(By.cssSelector(".example > h3")).text should be("File Uploaded!")
    }
  }

  // TODO: Add device-specific features for file upload support
  // def fileUpload(fileInput: By)(chooser: List[LocalImage] => List[LocalImage])
}