package com.outr.robobrowser.appium

import com.outr.robobrowser.{BrowserOptions, Device, RoboBrowser}
import io.appium.java_client.ios.{IOSDriver, IOSElement}
import io.youi.net._
import org.openqa.selenium.{By, WebDriver}
import org.openqa.selenium.chrome.ChromeOptions

class RoboIOS(override val options: IOSOptions = IOSOptions()) extends RoboBrowser {
  override protected def driver: IOSDriver[IOSElement] = super.driver.asInstanceOf[IOSDriver[IOSElement]]

  override protected def createWebDriver(options: ChromeOptions): WebDriver = {
    val url = new java.net.URL(this.options.url.toString())
    new IOSDriver[IOSElement](url, options)
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
    firstBy(By.name("Allow")) match {
      case Some(e) =>
        e.click()
        true
      case None => false
    }
  }
}

case class IOSOptions(headless: Boolean = false,
                      device: Device = Device.iPhone12ProMax,
                      fakeMedia: Boolean = true,
                      url: URL = url"http://localhost:4444") extends BrowserOptions