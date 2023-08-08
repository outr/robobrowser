package com.outr.robobrowser.browser.ios

import com.google.common.collect.ImmutableMap
import com.outr.robobrowser.{By, Context, RoboBrowser}
import com.outr.robobrowser.appium.{Appium, BundleId}
import io.appium.java_client.ios.IOSDriver
import org.openqa.selenium.{Capabilities, ImmutableCapabilities}
import spice.net._

import scala.concurrent.duration._
import scala.jdk.CollectionConverters.MapHasAsJava

class IOS(url: URL, capabilities: Capabilities) extends RoboBrowser(capabilities) with Appium {
  override type Driver = IOSDriver

  override def version: Double = capabilities.getCapability("os_version").toString.toDouble

  override lazy val sessionId: String = withDriver(_.getSessionId.toString)

  override def nativeAllow(reject: Boolean = false): Boolean = {
    val path = if (reject) {
      IOS.RejectXPath
    } else {
      IOS.AllowXPath
    }
    avoidStaleReference {
      firstBy(By.xPath(path, Context.Native)) match {
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
  def selectPhotos(uploadButton: By = By.xPath("//XCUIElementTypeButton[@name=\"Choose File\"]", Context.Native))
                  (filter: List[IOSFile] => List[IOSFile]): Unit = {
    // Allow uploads
    nativeAllow()

    // Click the file upload button
    if (firstBy(By.name("Photo Library", Context.Native)).isEmpty) {
      firstBy(uploadButton).foreach(_.click())
    }

    // Select the 'Photo Library'
    oneBy(By.name("Photo Library", Context.Native)).click()

    // Select 'All Photos'
    if (version == 13) avoidStaleReference {
      waitForResult(15.seconds) {
        firstBy(By.name("All Photos", Context.Native))
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
      by(By.xPath(imagePath, Context.Native))
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
      oneBy(By.xPath("//XCUIElementTypeButton[@label=\"Done\"]", Context.Native)).click()
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
    firstBy(By.xPath(choosePath, Context.Native)).foreach(_.click())
  }

  override protected def createDriver(): Driver = {
    val javaURL = new java.net.URI(url.toString()).toURL
    new IOSDriver(javaURL, capabilities)
  }
}

object IOS {
  private lazy val AllowXPath: String = "//*[@name='Allow' or @name='OK']"
  private lazy val RejectXPath: String = "//*[@name='Don't Allow' or @name='Deny']"

  private def caps(capabilities: (String, Any)*): IOSOptions = {
    val map = Map[String, Any](
      "os" -> "ios",
      "browser" -> "iphone",
      "real_mobile" -> true,
      "nativeWebTap" -> true,
      "automationName" -> "XCUITest"
    ) ++ capabilities.toMap
    IOSOptions(new ImmutableCapabilities(map.asJava), Nil)
  }

  object `iPhone 8 Plus` {
    def `v12`: IOSOptions = caps(
      "os_version" -> 12,
      "device" -> "iPhone 8 Plus"
    )

    def `v11`: IOSOptions = caps(
      "os_version" -> 11,
      "device" -> "iPhone 8 Plus"
    )
  }

  object `iPad Pro 12.9 2017` {
    def `v11`: IOSOptions = caps(
      "os_version" -> 11,
      "device" -> "iPad Pro 12.9 2017"
    )
  }

  object `iPhone XS Max` {
    def `v12`: IOSOptions = caps(
      "os_version" -> 12,
      "device" -> "iPhone XS Max"
    )
  }

  object `iPhone 7` {
    def `v12`: IOSOptions = caps(
      "os_version" -> 12,
      "device" -> "iPhone 7"
    )

    def `v10`: IOSOptions = caps(
      "os_version" -> 10,
      "device" -> "iPhone 7"
    )
  }

  object `iPad 5th` {
    def `v11`: IOSOptions = caps(
      "os_version" -> 11,
      "device" -> "iPad 5th"
    )
  }

  object `iPhone X` {
    def `v11`: IOSOptions = caps(
      "os_version" -> 11,
      "device" -> "iPhone X"
    )
  }

  object `iPhone 6S Plus` {
    def `v11`: IOSOptions = caps(
      "os_version" -> 11,
      "device" -> "iPhone 6S Plus"
    )
  }

  object `iPad Mini 4` {
    def `v11`: IOSOptions = caps(
      "os_version" -> 11,
      "device" -> "iPad Mini 4"
    )
  }

  object `iPad 7th` {
    def `v13`: IOSOptions = caps(
      "os_version" -> 13,
      "device" -> "iPad 7th"
    )
  }

  object `iPhone 11` {
    def `v14`: IOSOptions = caps(
      "os_version" -> 14,
      "device" -> "iPhone 11"
    )

    def `v13`: IOSOptions = caps(
      "os_version" -> 13,
      "device" -> "iPhone 11"
    )
  }

  object `iPhone 11 Pro Max` {
    def `v14`: IOSOptions = caps(
      "os_version" -> 14,
      "device" -> "iPhone 11 Pro Max"
    )

    def `v13`: IOSOptions = caps(
      "os_version" -> 13,
      "device" -> "iPhone 11 Pro Max"
    )
  }

  object `iPad Pro 12.9 2021` {
    def `v14`: IOSOptions = caps(
      "os_version" -> 14,
      "device" -> "iPad Pro 12.9 2021"
    )
  }

  object `iPad Pro 12.9 2020` {
    def `v14`: IOSOptions = caps(
      "os_version" -> 14,
      "device" -> "iPad Pro 12.9 2020"
    )

    def `v13`: IOSOptions = caps(
      "os_version" -> 13,
      "device" -> "iPad Pro 12.9 2020"
    )
  }

  object `iPad Air 4` {
    def `v14`: IOSOptions = caps(
      "os_version" -> 14,
      "device" -> "iPad Air 4"
    )
  }

  object `iPad 6th` {
    def `v11`: IOSOptions = caps(
      "os_version" -> 11,
      "device" -> "iPad 6th"
    )
  }

  object `iPad Air 2019` {
    def `v13`: IOSOptions = caps(
      "os_version" -> 13,
      "device" -> "iPad Air 2019"
    )

    def `v12`: IOSOptions = caps(
      "os_version" -> 12,
      "device" -> "iPad Air 2019"
    )
  }

  object `iPad Pro 11 2018` {
    def `v12`: IOSOptions = caps(
      "os_version" -> 12,
      "device" -> "iPad Pro 11 2018"
    )
  }

  object `iPhone XR` {
    def `v12`: IOSOptions = caps(
      "os_version" -> 12,
      "device" -> "iPhone XR"
    )
  }

  object `iPhone 11 Pro` {
    def `v15`: IOSOptions = caps(
      "os_version" -> 15,
      "device" -> "iPhone 11 Pro"
    )

    def `v13`: IOSOptions = caps(
      "os_version" -> 13,
      "device" -> "iPhone 11 Pro"
    )
  }

  object `iPhone 12 Pro Max` {
    def `v14`: IOSOptions = caps(
      "os_version" -> 14,
      "device" -> "iPhone 12 Pro Max"
    )
  }

  object `iPad 8th` {
    def `v14`: IOSOptions = caps(
      "os_version" -> 14,
      "device" -> "iPad 8th"
    )
  }

  object `iPad Pro 12.9 2018` {
    def `v15`: IOSOptions = caps(
      "os_version" -> 15,
      "device" -> "iPad Pro 12.9 2018"
    )

    def `v13`: IOSOptions = caps(
      "os_version" -> 13,
      "device" -> "iPad Pro 12.9 2018"
    )

    def `v12`: IOSOptions = caps(
      "os_version" -> 12,
      "device" -> "iPad Pro 12.9 2018"
    )
  }

  object `iPhone 6S` {
    def `v12`: IOSOptions = caps(
      "os_version" -> 12,
      "device" -> "iPhone 6S"
    )

    def `v11`: IOSOptions = caps(
      "os_version" -> 11,
      "device" -> "iPhone 6S"
    )
  }

  object `iPad Mini 2019` {
    def `v13`: IOSOptions = caps(
      "os_version" -> 13,
      "device" -> "iPad Mini 2019"
    )

    def `v12`: IOSOptions = caps(
      "os_version" -> 12,
      "device" -> "iPad Mini 2019"
    )
  }

  object `iPhone SE` {
    def `v11`: IOSOptions = caps(
      "os_version" -> 11,
      "device" -> "iPhone SE"
    )
  }

  object `iPhone 6` {
    def `v11`: IOSOptions = caps(
      "os_version" -> 11,
      "device" -> "iPhone 6"
    )
  }

  object `iPad Pro 11 2020` {
    def `v13`: IOSOptions = caps(
      "os_version" -> 13,
      "device" -> "iPad Pro 11 2020"
    )
  }

  object `iPhone 7 Plus` {
    def `v10`: IOSOptions = caps(
      "os_version" -> 10,
      "device" -> "iPhone 7 Plus"
    )
  }

  object `iPhone 12 Mini` {
    def `v14`: IOSOptions = caps(
      "os_version" -> 14,
      "device" -> "iPhone 12 Mini"
    )
  }

  object `iPad Pro 9.7 2016` {
    def `v11`: IOSOptions = caps(
      "os_version" -> 11,
      "device" -> "iPad Pro 9.7 2016"
    )
  }

  object `iPhone 12 Pro` {
    def `v14`: IOSOptions = caps(
      "os_version" -> 14,
      "device" -> "iPhone 12 Pro"
    )
  }

  object `iPhone 12` {
    def `v14`: IOSOptions = caps(
      "os_version" -> 14,
      "device" -> "iPhone 12"
    )
  }

  object `iPhone SE 2020` {
    def `v13`: IOSOptions = caps(
      "os_version" -> 13,
      "device" -> "iPhone SE 2020"
    )
  }

  object `iPhone 8` {
    def `v15`: IOSOptions = caps(
      "os_version" -> 15,
      "device" -> "iPhone 8"
    )

    def `v13`: IOSOptions = caps(
      "os_version" -> 13,
      "device" -> "iPhone 8"
    )

    def `v12`: IOSOptions = caps(
      "os_version" -> 12,
      "device" -> "iPhone 8"
    )

    def `v11`: IOSOptions = caps(
      "os_version" -> 11,
      "device" -> "iPhone 8"
    )
  }

  object `iPhone XS` {
    def `v15`: IOSOptions = caps(
      "os_version" -> 15,
      "device" -> "iPhone XS"
    )

    def `v14`: IOSOptions = caps(
      "os_version" -> 14,
      "device" -> "iPhone XS"
    )

    def `v13`: IOSOptions = caps(
      "os_version" -> 13,
      "device" -> "iPhone XS"
    )

    def `v12`: IOSOptions = caps(
      "os_version" -> 12,
      "device" -> "iPhone XS"
    )
  }

  object `iPad Pro 11 2021` {
    def `v14`: IOSOptions = caps(
      "os_version" -> 14,
      "device" -> "iPad Pro 11 2021"
    )
  }
}