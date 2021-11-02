package com.outr.robobrowser.chrome

import com.outr.robobrowser.RoboBrowser
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.{ChromeOptions => SeleniumChromeOptions}

class RoboChrome(override val options: ChromeOptions = ChromeOptions()) extends RoboBrowser {
  override protected def createWebDriver(options: SeleniumChromeOptions): WebDriver = {
    System.setProperty("webdriver.chrome.driver", this.options.driverPath)
    new ChromeDriver(options)
  }

  override def sessionId: String = "chrome"
}