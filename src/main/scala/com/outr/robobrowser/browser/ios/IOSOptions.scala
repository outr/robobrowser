package com.outr.robobrowser.browser.ios

import com.outr.robobrowser.RoboBrowser
import com.outr.robobrowser.browser.BrowserOptions
import org.openqa.selenium.Capabilities
import spice.net._

case class IOSOptions(capabilities: Capabilities,
                      postInit: List[RoboBrowser => Unit]) extends BrowserOptions[IOSOptions] {
  override def withPostInit(f: RoboBrowser => Unit): IOSOptions = copy(postInit = postInit ::: List(f))

  override def merge(capabilities: Capabilities): IOSOptions = copy(this.capabilities.merge(capabilities))

  def create(url: URL = URL.parse(typed[String]("url", "http://localhost:4444"))): IOS = {
    val b = new IOS(url, capabilities)
    postInit.foreach(f => f(b))
    b
  }
}