package com.outr.robobrowser.appium

import com.google.common.collect.ImmutableMap
import com.outr.robobrowser.{Capabilities, Context, RoboBrowser}
import io.appium.java_client.ios.IOSDriver
import org.openqa.selenium.By
import org.openqa.selenium.chrome.ChromeOptions

import scala.concurrent.duration.DurationInt

class RoboIOS(override val capabilities: Capabilities) extends RoboBrowser(capabilities) with Appium {
  override type Driver = IOSDriver

  override lazy val version: Double = capabilities.typed[Int]("os_version").toDouble

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
   * @param uploadButton is used only for iOS 15 where native interaction is required to launch the file chooser.
   *                     This defaults to //XCUIElementTypeButton[@name="Choose File"]
   * @param filter the filter to apply to the list of photos returning the list that will be selected
   */
  def selectPhotos(uploadButton: By = By.xpath("//XCUIElementTypeButton[@name=\"Choose File\"]"))
                  (filter: List[IOSFile] => List[IOSFile]): Unit = {
    // Allow uploads
    nativeAllow()

    // Click the file upload button
    if (firstBy(By.name("Photo Library"), Context.Native).isEmpty) {
      firstBy(uploadButton, Context.Native).foreach(_.click())
    }

    // Select the 'Photo Library'
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
    val chooseLabel = if (filtered.length > 1) {
      "Add"
    } else {
      "Choose"
    }
    val choosePath = if (version == 15) {
      s"""//XCUIElementTypeButton[@label="$chooseLabel"]"""
    } else {
      s"""//XCUIElementTypeButton[@name="$chooseLabel"]"""
    }
    firstBy(By.xpath(choosePath), Context.Native).foreach(_.click())
  }
}

object RoboIOS {
  lazy val AllowXPath: String = "//*[@name='Allow' or @name='OK']"
  lazy val RejectXPath: String = "//*[@name='Don't Allow' or @name='Deny']"

  def create(capabilities: Capabilities): RoboIOS = new RoboIOS(capabilities)
}