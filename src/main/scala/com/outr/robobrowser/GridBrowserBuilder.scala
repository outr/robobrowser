//package com.outr.robobrowser
//
//import org.openqa.selenium.chrome.ChromeOptions
//import org.openqa.selenium.remote.{FileDetector, RemoteWebDriver}
//
//object GridBrowserBuilder {
//  def create(capabilities: Capabilities): RoboBrowser = {
//    new RoboBrowser(capabilities) {
//      override type Driver = RemoteWebDriver
//
//      override def sessionId: String = withDriver(_.getSessionId.toString)
//
//      override protected def createWebDriver(options: ChromeOptions): Driver = {
//        val url = new java.net.URL(capabilities.typed[String]("url", "http://localhost:4444/wd/hub"))
//        val driver = new RemoteWebDriver(url, options)
//        capabilities.getTyped[FileDetector]("fileDetector").foreach(driver.setFileDetector)
//        driver
//      }
//    }
//  }
//}