package com.outr.robobrowser.browser.remote

import com.outr.robobrowser.RoboBrowser
import org.openqa.selenium.{Capabilities, ImmutableCapabilities}
import org.openqa.selenium.remote.{FileDetector, RemoteWebDriver}
import spice.net._

class Remote(url: URL, capabilities: Capabilities, fileDetector: Option[FileDetector]) extends RoboBrowser(capabilities) {
  override type Driver = RemoteWebDriver

  override def sessionId: String = withDriver(_.getSessionId.toString)

  override protected def createDriver(): Driver = {
    val javaURL = new java.net.URI(url.toString()).toURL
    val driver = new RemoteWebDriver(javaURL, capabilities)
    fileDetector.foreach(driver.setFileDetector)
    driver
  }
}

object Remote extends RemoteOptions(new ImmutableCapabilities, None, Nil)