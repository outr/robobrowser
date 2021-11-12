//package com.outr.robobrowser.remote
//
//import com.outr.robobrowser.{Capabilities, RoboBrowser}
//import org.openqa.selenium.WebDriver
//import org.openqa.selenium.chrome.ChromeOptions
//import org.openqa.selenium.remote.RemoteWebDriver
//
//class RoboRemote(override val capabilities: Capabilities) extends RoboBrowser {
//  override protected def driver: RemoteWebDriver = super.driver.asInstanceOf[RemoteWebDriver]
//
//  override def sessionId: String = driver.getSessionId.toString
//
//  override protected def createWebDriver(options: ChromeOptions): WebDriver = {
//    val url = new java.net.URL(capabilities.typed[String]("url"))
//    new RemoteWebDriver(url, options)
//  }
//}