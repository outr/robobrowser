package spec

import com.outr.robobrowser.appium.RoboIOS
import com.outr.robobrowser.{By, Context, MobileBrowser}
import com.outr.robobrowser.integration.IntegrationTests
import spice.net._

import scala.concurrent.duration._

case class BrowserStackTests(label: String, browser: MobileBrowser) extends IntegrationTests[MobileBrowser] {
  "Browser Stack Mobile File Uploading" when {
    "loading the file uploader" in {
      browser.load(url"https://the-internet.herokuapp.com/upload")
      browser.waitForLoaded()
    }
    "clicking the file upload button" in {
      // iOS
      browser.oneBy(By.id("file-upload")).click()
    }
    "select the first photo" in {
      browser match {
        case ios: RoboIOS => ios.selectPhotos() { photos =>
          photos.take(1)
        }
        case _ => throw new UnsupportedOperationException("Unsupported RoboBrowser")
      }
    }
    "wait for animation to complete" in {
      browser.sleep(2.seconds)
    }
    "submit the file to be uploaded" in {
      browser.waitForFirst()(By.id("file-submit"), By.id("file-submit", Context.Native))
        .click()
    }
    // TODO: Support Android
    /*"click the upload button" in {
      browser.pushFile("/data/local/tmp/LICENSE", new File("LICENSE"))
      browser.oneBy(By.id("file-upload")).sendInput("/data/local/tmp/LICENSE")
      browser.sleep(2.seconds)
      browser.oneBy(By.id("file-submit")).click()
    }*/
    "verify the file uploaded" in {
      browser.waitForLoaded()
      browser.waitFor(15.seconds) {
        browser.avoidStaleReference {
          browser.oneBy(By.css(".example > h3")).text == "File Uploaded!"
        }
      } should be(true, s"File uploaded not detected!")
    }
  }
}