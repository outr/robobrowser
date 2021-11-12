package com.outr.robobrowser.appium

import com.outr.robobrowser.{Capabilities, RoboBrowser}
import io.appium.java_client.android.AndroidDriver
import org.openqa.selenium.{By, WebDriver}
import org.openqa.selenium.chrome.ChromeOptions

class RoboAndroid(capabilities: Capabilities) extends RoboBrowser(capabilities) with Appium {
  override protected def driver: AndroidDriver = super.driver.asInstanceOf[AndroidDriver]

  override def sessionId: String = driver.getSessionId.toString

  override protected def createWebDriver(options: ChromeOptions): WebDriver = {
    val url = new java.net.URL(capabilities.typed[String]("url"))
    new AndroidDriver(url, options)
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
      avoidStaleReference {
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

  def currentActivity(): Activity = Activity(driver.getCurrentPackage, driver.currentActivity())

  def startActivity(activity: Activity): Unit = driver.startActivity(new io.appium.java_client.android.Activity(activity.packageName, activity.name))

  override def home(): Unit = keyboard.send.home()
}

object RoboAndroid {
  private lazy val AllowXPath: String = ".//android.widget.Button[@resource-id='com.android.chrome:id/positive_button' or @text='Allow' or @text=\"While using the app\"]"
  private lazy val RejectXPath: String = ".//android.widget.Button[@resource-id='com.android.chrome:id/negative_button']"

  def create(capabilities: Capabilities): RoboAndroid = new RoboAndroid(capabilities)
}