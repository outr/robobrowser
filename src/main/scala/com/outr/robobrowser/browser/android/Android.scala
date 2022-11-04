package com.outr.robobrowser.browser.android

import com.outr.robobrowser.appium.{Activity, Appium}
import com.outr.robobrowser.{By, Context, RoboBrowser}
import io.appium.java_client.android.AndroidDriver
import org.openqa.selenium.{Capabilities, ImmutableCapabilities, OutputType, TakesScreenshot}
import spice.net.URL

import scala.jdk.CollectionConverters._

class Android(url: URL, capabilities: Capabilities) extends RoboBrowser(capabilities) with Appium {
  override type Driver = AndroidDriver

  override lazy val version: Double = capabilities.getCapability("os_version").toString.toDouble

  override lazy val sessionId: String = withDriver(_.getSessionId.toString)

  override def capture(): Array[Byte] = withDriverAndContext(Context.Browser)(
    _.asInstanceOf[TakesScreenshot].getScreenshotAs(OutputType.BYTES)
  )

  override def nativeAllow(reject: Boolean = false): Boolean = {
    val path = if (reject) {
      Android.RejectXPath
    } else {
      Android.AllowXPath
    }
    avoidStaleReference {
      firstBy(By.xPath(path, Context.Native)) match {
        case Some(e) =>
          e.click()
          nativeAllow(reject)
          true
        case None => false
      }
    }
  }

  def currentActivity(): Activity = withDriver { driver =>
    Activity(driver.getCurrentPackage, driver.currentActivity())
  }

  def startActivity(activity: Activity): Unit = withDriver { driver =>
    driver.startActivity(new io.appium.java_client.android.Activity(activity.packageName, activity.name))
  }

  override def home(): Unit = keyboard.send.home()

  override protected lazy val _driver: Driver = {
    val javaURL = new java.net.URL(url.toString())
    new AndroidDriver(javaURL, capabilities)
  }
}

object Android {
  private lazy val AllowXPath: String = ".//android.widget.Button[@resource-id='com.android.chrome:id/positive_button' or @text='Allow' or @text=\"While using the app\"]"
  private lazy val RejectXPath: String = ".//android.widget.Button[@resource-id='com.android.chrome:id/negative_button']"

  private def caps(capabilities: (String, Any)*): AndroidOptions = {
    val map = Map[String, Any](
      "os" -> "android",
      "browser" -> "android",
      "real_mobile" -> true,
      "automationName" -> "UIAutomator2"
    ) ++ capabilities.toMap
    AndroidOptions(new ImmutableCapabilities(map.asJava))
  }

  object `Vivo Y50` {
    def `v10.0`: AndroidOptions = caps(
      "os_version" -> 10.0,
      "device" -> "Vivo Y50"
    )
  }

  object `Samsung Galaxy Tab S4` {
    def `v8.1`: AndroidOptions = caps(
      "os_version" -> 8.1,
      "device" -> "Samsung Galaxy Tab S4"
    )
  }

  object `Samsung Galaxy Note 20 Ultra` {
    def `v10.0`: AndroidOptions = caps(
      "os_version" -> 10.0,
      "device" -> "Samsung Galaxy Note 20 Ultra"
    )
  }

  object `Samsung Galaxy S20` {
    def `v10.0`: AndroidOptions = caps(
      "os_version" -> 10.0,
      "device" -> "Samsung Galaxy S20"
    )
  }

  object `OnePlus 7` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "OnePlus 7"
    )
  }

  object `Xiaomi Redmi Note 9` {
    def `v10.0`: AndroidOptions = caps(
      "os_version" -> 10.0,
      "device" -> "Xiaomi Redmi Note 9"
    )
  }

  object `Google Pixel 2` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "Google Pixel 2"
    )

    def `v8.0`: AndroidOptions = caps(
      "os_version" -> 8.0,
      "device" -> "Google Pixel 2"
    )
  }

  object `Samsung Galaxy S20 Ultra` {
    def `v10.0`: AndroidOptions = caps(
      "os_version" -> 10.0,
      "device" -> "Samsung Galaxy S20 Ultra"
    )
  }

  object `OnePlus 7T` {
    def `v10.0`: AndroidOptions = caps(
      "os_version" -> 10.0,
      "device" -> "OnePlus 7T"
    )
  }

  object `Motorola Moto G7 Play` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "Motorola Moto G7 Play"
    )
  }

  object `Samsung Galaxy S21 Ultra` {
    def `v11.0`: AndroidOptions = caps(
      "os_version" -> 11.0,
      "device" -> "Samsung Galaxy S21 Ultra"
    )
  }

  object `OnePlus 6T` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "OnePlus 6T"
    )
  }

  object `Google Pixel 3a XL` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "Google Pixel 3a XL"
    )
  }

  object `Google Pixel 4` {
    def `v11.0`: AndroidOptions = caps(
      "os_version" -> 11.0,
      "device" -> "Google Pixel 4"
    )

    def `v10.0`: AndroidOptions = caps(
      "os_version" -> 10.0,
      "device" -> "Google Pixel 4"
    )
  }

  object `Google Pixel 3` {
    def `v10.0`: AndroidOptions = caps(
      "os_version" -> 10.0,
      "device" -> "Google Pixel 3"
    )

    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "Google Pixel 3"
    )
  }

  object `Samsung Galaxy J7 Prime` {
    def `v8.1`: AndroidOptions = caps(
      "os_version" -> 8.1,
      "device" -> "Samsung Galaxy J7 Prime"
    )
  }

  object `Google Nexus 5` {
    def `v4.4`: AndroidOptions = caps(
      "os_version" -> 4.4,
      "device" -> "Google Nexus 5"
    )
  }

  object `Samsung Galaxy S7` {
    def `v6.0`: AndroidOptions = caps(
      "os_version" -> 6.0,
      "device" -> "Samsung Galaxy S7"
    )
  }

  object `Huawei P30` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "Huawei P30"
    )
  }

  object `Samsung Galaxy A8` {
    def `v7.1`: AndroidOptions = caps(
      "os_version" -> 7.1,
      "device" -> "Samsung Galaxy A8"
    )
  }

  object `Samsung Galaxy Tab S6` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "Samsung Galaxy Tab S6"
    )
  }

  object `Samsung Galaxy S10e` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "Samsung Galaxy S10e"
    )
  }

  object `Samsung Galaxy Tab 4` {
    def `v4.4`: AndroidOptions = caps(
      "os_version" -> 4.4,
      "device" -> "Samsung Galaxy Tab 4"
    )
  }

  object `Samsung Galaxy S21 Plus` {
    def `v11.0`: AndroidOptions = caps(
      "os_version" -> 11.0,
      "device" -> "Samsung Galaxy S21 Plus"
    )
  }

  object `Samsung Galaxy S8 Plus` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "Samsung Galaxy S8 Plus"
    )

    def `v7.0`: AndroidOptions = caps(
      "os_version" -> 7.0,
      "device" -> "Samsung Galaxy S8 Plus"
    )
  }

  object `Samsung Galaxy S20 Plus` {
    def `v10.0`: AndroidOptions = caps(
      "os_version" -> 10.0,
      "device" -> "Samsung Galaxy S20 Plus"
    )
  }

  object `Motorola Moto G9 Play` {
    def `v10.0`: AndroidOptions = caps(
      "os_version" -> 10.0,
      "device" -> "Motorola Moto G9 Play"
    )
  }

  object `OnePlus 8` {
    def `v10.0`: AndroidOptions = caps(
      "os_version" -> 10.0,
      "device" -> "OnePlus 8"
    )
  }

  object `Google Pixel 5` {
    def `v11.0`: AndroidOptions = caps(
      "os_version" -> 11.0,
      "device" -> "Google Pixel 5"
    )

    def `v12.0`: AndroidOptions = caps(
      "os_version" -> 12.0,
      "device" -> "Google Pixel 5"
    )
  }

  object `Samsung Galaxy Note 10` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "Samsung Galaxy Note 10"
    )
  }

  object `Google Nexus 6` {
    def `v6.0`: AndroidOptions = caps(
      "os_version" -> 6.0,
      "device" -> "Google Nexus 6"
    )

    def `v5.0`: AndroidOptions = caps(
      "os_version" -> 5.0,
      "device" -> "Google Nexus 6"
    )
  }

  object `Google Pixel` {
    def `v8.0`: AndroidOptions = caps(
      "os_version" -> 8.0,
      "device" -> "Google Pixel"
    )

    def `v7.1`: AndroidOptions = caps(
      "os_version" -> 7.1,
      "device" -> "Google Pixel"
    )
  }

  object `Google Pixel 4 XL` {
    def `v10.0`: AndroidOptions = caps(
      "os_version" -> 10.0,
      "device" -> "Google Pixel 4 XL"
    )
  }

  object `Samsung Galaxy S9` {
    def `v8.0`: AndroidOptions = caps(
      "os_version" -> 8.0,
      "device" -> "Samsung Galaxy S9"
    )
  }

  object `Google Pixel 3a` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "Google Pixel 3a"
    )
  }

  object `Samsung Galaxy S10` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "Samsung Galaxy S10"
    )
  }

  object `Google Pixel 3 XL` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "Google Pixel 3 XL"
    )
  }

  object `Samsung Galaxy S9 Plus` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "Samsung Galaxy S9 Plus"
    )

    def `v8.0`: AndroidOptions = caps(
      "os_version" -> 8.0,
      "device" -> "Samsung Galaxy S9 Plus"
    )
  }

  object `Samsung Galaxy Tab S5e` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "Samsung Galaxy Tab S5e"
    )
  }

  object `Samsung Galaxy Tab S7` {
    def `v10.0`: AndroidOptions = caps(
      "os_version" -> 10.0,
      "device" -> "Samsung Galaxy Tab S7"
    )
  }

  object `Samsung Galaxy A10` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "Samsung Galaxy A10"
    )
  }

  object `Samsung Galaxy Note 9` {
    def `v8.1`: AndroidOptions = caps(
      "os_version" -> 8.1,
      "device" -> "Samsung Galaxy Note 9"
    )
  }

  object `Oppo Reno 3 Pro` {
    def `v10.0`: AndroidOptions = caps(
      "os_version" -> 10.0,
      "device" -> "Oppo Reno 3 Pro"
    )
  }

  object `OnePlus 9` {
    def `v11.0`: AndroidOptions = caps(
      "os_version" -> 11.0,
      "device" -> "OnePlus 9"
    )
  }

  object `Samsung Galaxy S10 Plus` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "Samsung Galaxy S10 Plus"
    )
  }

  object `Samsung Galaxy A11` {
    def `v10.0`: AndroidOptions = caps(
      "os_version" -> 10.0,
      "device" -> "Samsung Galaxy A11"
    )
  }

  object `Samsung Galaxy S21` {
    def `v11.0`: AndroidOptions = caps(
      "os_version" -> 11.0,
      "device" -> "Samsung Galaxy S21"
    )
  }

  object `Samsung Galaxy Note 20` {
    def `v10.0`: AndroidOptions = caps(
      "os_version" -> 10.0,
      "device" -> "Samsung Galaxy Note 20"
    )
  }

  object `Samsung Galaxy S6` {
    def `v5.0`: AndroidOptions = caps(
      "os_version" -> 5.0,
      "device" -> "Samsung Galaxy S6"
    )
  }

  object `Samsung Galaxy S8` {
    def `v7.0`: AndroidOptions = caps(
      "os_version" -> 7.0,
      "device" -> "Samsung Galaxy S8"
    )
  }

  object `Xiaomi Redmi Note 8` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "Xiaomi Redmi Note 8"
    )
  }

  object `Google Nexus 9` {
    def `v5.1`: AndroidOptions = caps(
      "os_version" -> 5.1,
      "device" -> "Google Nexus 9"
    )
  }

  object `Samsung Galaxy Note 8` {
    def `v7.1`: AndroidOptions = caps(
      "os_version" -> 7.1,
      "device" -> "Samsung Galaxy Note 8"
    )
  }

  object `Motorola Moto X 2nd Gen` {
    def `v6.0`: AndroidOptions = caps(
      "os_version" -> 6.0,
      "device" -> "Motorola Moto X 2nd Gen"
    )

    def `v5.0`: AndroidOptions = caps(
      "os_version" -> 5.0,
      "device" -> "Motorola Moto X 2nd Gen"
    )
  }

  object `Samsung Galaxy A51` {
    def `v10.0`: AndroidOptions = caps(
      "os_version" -> 10.0,
      "device" -> "Samsung Galaxy A51"
    )
  }

  object `Samsung Galaxy Note 10 Plus` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "Samsung Galaxy Note 10 Plus"
    )
  }

  object `Samsung Galaxy Tab S3` {
    def `v8.0`: AndroidOptions = caps(
      "os_version" -> 8.0,
      "device" -> "Samsung Galaxy Tab S3"
    )
  }

  object `Xiaomi Redmi Note 7` {
    def `v9.0`: AndroidOptions = caps(
      "os_version" -> 9.0,
      "device" -> "Xiaomi Redmi Note 7"
    )
  }
}