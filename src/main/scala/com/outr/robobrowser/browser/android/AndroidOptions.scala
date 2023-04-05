package com.outr.robobrowser.browser.android

import com.outr.robobrowser.RoboBrowser
import com.outr.robobrowser.browser.BrowserOptions
import org.openqa.selenium.Capabilities
import spice.net._

case class AndroidOptions(capabilities: Capabilities,
                          postInit: List[RoboBrowser => Unit]) extends BrowserOptions[AndroidOptions] {
  override def withPostInit(f: RoboBrowser => Unit): AndroidOptions = copy(postInit = postInit ::: List(f))

  override def merge(capabilities: Capabilities): AndroidOptions = copy(this.capabilities.merge(capabilities))

  def create(url: URL = typed[URL]("url", url"http://localhost:4444")): Android = {
    val b = new Android(url, capabilities)
    postInit.foreach(f => f(b))
    b
  }
}