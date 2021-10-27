package com.outr.robobrowser.appium

import com.outr.robobrowser.RoboBrowser
import io.appium.java_client.android.{AndroidDriver, AndroidElement}
import org.openqa.selenium.{By, WebDriver}
import org.openqa.selenium.chrome.ChromeOptions

class RoboAndroid(override val options: AndroidOptions = AndroidOptions()) extends RoboBrowser with Appium {
  override protected def driver: AndroidDriver[AndroidElement] = super.driver.asInstanceOf[AndroidDriver[AndroidElement]]

  override protected def createWebDriver(options: ChromeOptions): WebDriver = {
    val url = new java.net.URL(this.options.url.toString())
    new AndroidDriver[AndroidElement](url, options)
  }

  override def inNativeContext[Return](f: => Return): Return = {
    val context = driver.getContext
    driver.context("NATIVE_APP")
    try {
      f
    } finally {
      driver.context(context)
    }
  }

  override def nativeAllow(reject: Boolean = false): Unit = {
    inNativeContext {
      val path = if (reject) {
        RoboAndroid.RejectXPath
      } else {
        RoboAndroid.AllowXPath
      }
      firstBy(By.xpath(path)) match {
        case Some(e) =>
          e.click()
          nativeAllow(reject)
          true
        case None => false
      }
    }
  }
}

object RoboAndroid {
  private lazy val AllowXPath: String = ".//android.widget.Button[@resource-id='com.android.chrome:id/positive_button' or @text='Allow' or @text=\"While using the app\"]"
  private lazy val RejectXPath: String = ".//android.widget.Button[@resource-id='com.android.chrome:id/negative_button']"
}