package com.outr.robobrowser.browser.android

import com.outr.robobrowser.browser.BrowserOptions
import org.openqa.selenium.Capabilities
import spice.net._

case class AndroidOptions(capabilities: Capabilities) extends BrowserOptions[AndroidOptions] {
  override def merge(capabilities: Capabilities): AndroidOptions = copy(this.capabilities.merge(capabilities))

  def create(url: URL = typed[URL]("url", url"http://localhost:4444")): Android = new Android(url, capabilities)
}