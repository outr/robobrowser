package com.outr.robobrowser.browser.ios

import org.openqa.selenium.Capabilities
import spice.net._

case class IOSOptions(capabilities: Capabilities) {
  def create(url: URL = url"http://localhost:4444"): IOS = new IOS(url, capabilities)
}