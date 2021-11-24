package spec

import com.outr.robobrowser.appium.RoboIOS
import com.outr.robobrowser.{Context, MobileBrowser}
import com.outr.robobrowser.integration.IntegrationTests
import com.outr.robobrowser.monitor.BrowserMonitor
import io.youi.net._
import org.openqa.selenium.By

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
      browser match {
        case ios: RoboIOS => ios.selectPhotos() { photos =>
          photos.take(1)
        }
        case _ => throw new UnsupportedOperationException("Unsupported RoboBrowser")
      }
      browser.sleep(2.seconds)
      browser.oneBy(By.id("file-submit")).click()
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
          browser.oneBy(By.cssSelector(".example > h3")).text == "File Uploaded!"
        }
      } should be(true, s"File uploaded not detected!")
    }
  }
}