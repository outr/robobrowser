//package com.outr.robobrowser.chrome
//
//import com.outr.robobrowser.{Capabilities, RoboBrowser}
//import org.openqa.selenium.WebDriver
//import org.openqa.selenium.chrome.ChromeDriver
//import org.openqa.selenium.chrome.{ChromeOptions => SeleniumChromeOptions}
//
//class RoboChrome(override val capabilities: Capabilities = RoboChrome.driverPath()) extends RoboBrowser {
//  override protected def createWebDriver(options: SeleniumChromeOptions): WebDriver = {
//    System.setProperty("webdriver.chrome.driver", capabilities.typed[String]("driverPath"))
//    new ChromeDriver(options)
//  }
//
//  override def sessionId: String = "chrome"
//}
//
//object RoboChrome {
//  def driverPath(path: String = "/usr/bin/chromedriver"): Capabilities = Capabilities("driverPath" -> path)
//}