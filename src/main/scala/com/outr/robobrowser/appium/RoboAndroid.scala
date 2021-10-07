package com.outr.robobrowser.appium

import com.outr.robobrowser.RoboBrowser
import io.appium.java_client.android.{AndroidDriver, AndroidElement}
import org.openqa.selenium.{By, WebDriver}
import org.openqa.selenium.chrome.ChromeOptions

class RoboAndroid(override val options: AndroidOptions = AndroidOptions()) extends RoboBrowser {
  override protected def driver: AndroidDriver[AndroidElement] = super.driver.asInstanceOf[AndroidDriver[AndroidElement]]

  override protected def createWebDriver(options: ChromeOptions): WebDriver = {
    val url = new java.net.URL(this.options.url.toString())
    new AndroidDriver[AndroidElement](url, options)
  }

  def inNativeContext[Return](f: => Return): Return = {
    val context = driver.getContext
    driver.context("NATIVE_APP")
    try {
      f
    } finally {
      driver.context(context)
    }
  }

  def nativeAllow(): Boolean = inNativeContext {
    firstBy(By.xpath(".//android.widget.Button[@text='Allow']")) match {
      case Some(e) =>
        e.click()
        nativeAllow()
        true
      case None => false
    }
  }
}