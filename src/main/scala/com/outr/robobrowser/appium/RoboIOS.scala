package com.outr.robobrowser.appium

import com.google.common.collect.ImmutableMap
import com.outr.robobrowser.{Capabilities, RoboBrowser}
import io.appium.java_client.ios.IOSDriver
import org.openqa.selenium.{By, WebDriver}
import org.openqa.selenium.chrome.ChromeOptions

class RoboIOS(override val capabilities: Capabilities) extends RoboBrowser(capabilities) with Appium {
  override type Driver = IOSDriver

  override def sessionId: String = withDriver(_.getSessionId.toString)

  override protected def createWebDriver(options: ChromeOptions): Driver = {
    val url = new java.net.URL(capabilities.typed[String]("url", "http://localhost:4444"))
    new IOSDriver(url, options)
  }

  override def inNativeContext[Return](f: => Return): Return = withDriver { driver =>
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

  def activate(bundleId: BundleId): Unit = withDriver(_.activateApp(bundleId.id))

  override def home(): Unit = withDriver(_.executeScript("mobile: pressButton", ImmutableMap.of("name", "home")))
}

object RoboIOS {
  lazy val AllowXPath: String = "//*[@name='Allow' or @name='OK']"
  lazy val RejectXPath: String = "//*[@name='Don't Allow' or @name='Deny']"

  def create(capabilities: Capabilities): RoboIOS = new RoboIOS(capabilities)
}