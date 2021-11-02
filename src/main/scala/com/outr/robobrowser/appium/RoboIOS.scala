package com.outr.robobrowser.appium

import com.outr.robobrowser.RoboBrowser
import io.appium.java_client.ios.IOSDriver
import org.openqa.selenium.{By, WebDriver}
import org.openqa.selenium.chrome.ChromeOptions

class RoboIOS(override val options: IOSOptions = IOSOptions()) extends RoboBrowser with Appium {
  override protected def driver: IOSDriver = super.driver.asInstanceOf[IOSDriver]

  override def sessionId: String = driver.getSessionId.toString

  override protected def createWebDriver(options: ChromeOptions): WebDriver = {
    val url = new java.net.URL(this.options.url.toString())
    new IOSDriver(url, options)
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
        RoboIOS.RejectXPath
      } else {
        RoboIOS.AllowXPath
      }
      avoidStaleReference {
        firstBy(By.xpath(path)) match {
          case Some(e) =>
            e.click()
            true
          case None => false
        }
      }
    }
  }
}

object RoboIOS {
  lazy val AllowXPath: String = "//*[@name='Allow' or @name='OK']"
  lazy val RejectXPath: String = "//*[@name='Don't Allow' or @name='Deny']"
}