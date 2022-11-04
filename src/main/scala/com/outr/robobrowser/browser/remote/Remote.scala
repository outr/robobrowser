package com.outr.robobrowser.browser.remote

import com.outr.robobrowser.RoboBrowser
import org.openqa.selenium.{Capabilities, ImmutableCapabilities}
import org.openqa.selenium.remote.{FileDetector, RemoteWebDriver}
import spice.net._

class Remote(url: URL, capabilities: Capabilities, fileDetector: Option[FileDetector]) extends RoboBrowser {
  override type Driver = RemoteWebDriver

  override def sessionId: String = withDriver(_.getSessionId.toString)

  override protected lazy val _driver: Driver = {
    val javaURL = new java.net.URL(url.toString())
    val driver = new RemoteWebDriver(javaURL, capabilities)
    fileDetector.foreach(driver.setFileDetector)
    driver
  }
}

object Remote extends RemoteOptions(url"http://localhost:4444", new ImmutableCapabilities, None)