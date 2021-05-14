package com.outr.robobrowser

import io.youi.net.{URL, URLInterpolator}
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.remote.RemoteWebDriver

trait DriverLoader {
  def headless: Boolean

  def apply(options: ChromeOptions): WebDriver
}

object DriverLoader {
  case class Chrome(headless: Boolean = true, path: String = "/usr/bin/chromedriver") extends DriverLoader {
    override def apply(options: ChromeOptions): WebDriver = {
      System.setProperty("webdriver.chrome.driver", path)
      new ChromeDriver(options)
    }
  }

  case class Remote(url: URL = url"http://localhost:4444", headless: Boolean = false) extends DriverLoader {
    override def apply(options: ChromeOptions): WebDriver = new RemoteWebDriver(new java.net.URL(url.toString()), options)
  }
}