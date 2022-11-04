package com.outr.robobrowser.browser.android

import org.openqa.selenium.Capabilities
import spice.net._

case class AndroidOptions(capabilities: Capabilities) {
  def create(url: URL = url"http://localhost:4444"): Android = new Android(url, capabilities)
}