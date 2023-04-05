package com.outr.robobrowser.browser.remote

import com.outr.robobrowser.RoboBrowser
import com.outr.robobrowser.browser.BrowserOptions
import org.openqa.selenium.Capabilities
import org.openqa.selenium.remote.FileDetector
import spice.net._

case class RemoteOptions(capabilities: Capabilities,
                         fileDetector: Option[FileDetector],
                         postInit: List[RoboBrowser => Unit]) extends BrowserOptions[RemoteOptions] {
  override def withPostInit(f: RoboBrowser => Unit): RemoteOptions = copy(postInit = postInit ::: List(f))

  override def merge(capabilities: Capabilities): RemoteOptions = copy(capabilities = this.capabilities.merge(capabilities))

  def fileDetector(detector: FileDetector): RemoteOptions = copy(fileDetector = Some(detector))

  def grid: RemoteOptions = url(url"http://localhost:4444/wd/hub")

  def create(url: URL = typed[URL]("url", url"http://localhost:4444")): Remote = {
    val b = new Remote(url, capabilities, fileDetector)
    postInit.foreach(f => f(b))
    b
  }
}
