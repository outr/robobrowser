package com.outr.robobrowser.appium

import com.google.common.collect.ImmutableMap
import com.outr.robobrowser.monitor.BrowserMonitor
import com.outr.robobrowser.{Capabilities, Context, RoboBrowser, WebElement}
import io.appium.java_client.ios.IOSDriver
import org.openqa.selenium.{By, WebDriver}
import org.openqa.selenium.chrome.ChromeOptions

import scala.concurrent.duration.DurationInt

class RoboIOS(override val capabilities: Capabilities) extends RoboBrowser(capabilities) with Appium {
  override type Driver = IOSDriver

  lazy val version: Int = capabilities.typed[Int]("os_version")

  override def sessionId: String = withDriver(_.getSessionId.toString)

  override protected def createWebDriver(options: ChromeOptions): Driver = {
    val url = new java.net.URL(capabilities.typed[String]("url", "http://localhost:4444"))
    new IOSDriver(url, options)
  }

  override def nativeAllow(reject: Boolean = false): Boolean = {
    val path = if (reject) {
      RoboIOS.RejectXPath
    } else {
      RoboIOS.AllowXPath
    }
    avoidStaleReference {
      firstBy(By.xpath(path), Context.Native) match {
        case Some(e) =>
          e.click()
          true
        case None => false
      }
    }
  }

  def activate(bundleId: BundleId): Unit = withDriver(_.activateApp(bundleId.id))

  override def home(): Unit = withDriver(_.executeScript("mobile: pressButton", ImmutableMap.of("name", "home")))

  /**
   * Uses native context to select photos from the photo library. Assumes the file selection has already been opened.
   */
  def selectPhotos(filter: List[IOSFile] => List[IOSFile]): Unit = {
    // Allow uploads
    nativeAllow()

    // Select the 'Photo Library'
    if (version == 15) {
      oneBy(By.xpath("//XCUIElementTypeButton[@name=\"Choose File\"]"), Context.Native).click()
    }
    oneBy(By.name("Photo Library"), Context.Native).click()

    // Select 'All Photos'
    if (version == 13) avoidStaleReference {
      waitForResult(15.seconds) {
        firstBy(By.name("All Photos"), Context.Native)
      }.click()
    }

    // Wait for animation
    sleep(1.second)

    val imagePath = if (version == 13) {
      "//XCUIElementTypeCell"
    } else {
      "//XCUIElementTypeImage"
    }

    // Get all photos
    val images = waitForResult(15.seconds) {
      by(By.xpath(imagePath), Context.Native)
        .map(e => IOSFile(e)) match {
          case Nil => None
          case list => Some(list)
        }
    }

    // Filter photos
    val filtered = filter(images)

    // Select photos
    filtered.foreach(_.click())

    // Wait for animation
    sleep(1.second)

    // Finalize the selection
    if (version == 13) {
      oneBy(By.xpath("//XCUIElementTypeButton[@label=\"Done\"]"), Context.Native).click()
    }
    val choosePath = if (version == 15) {
      "//XCUIElementTypeButton[@label=\"Choose\"]"
    } else {
      "//XCUIElementTypeButton[@name=\"Choose\"]"
    }
    oneBy(By.xpath(choosePath), Context.Native).click()
  }
}

object RoboIOS {
  lazy val AllowXPath: String = "//*[@name='Allow' or @name='OK']"
  lazy val RejectXPath: String = "//*[@name='Don't Allow' or @name='Deny']"

  def create(capabilities: Capabilities): RoboIOS = new RoboIOS(capabilities)
}