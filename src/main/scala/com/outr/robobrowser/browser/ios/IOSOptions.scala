package com.outr.robobrowser.browser.ios

import com.outr.robobrowser.browser.BrowserOptions
import org.openqa.selenium.Capabilities
import spice.net._

case class IOSOptions(capabilities: Capabilities) extends BrowserOptions[IOSOptions] {
  override def merge(capabilities: Capabilities): IOSOptions = copy(this.capabilities.merge(capabilities))

  def create(url: URL = URL(typed[String]("url", "http://localhost:4444"))): IOS = new IOS(url, capabilities)
}