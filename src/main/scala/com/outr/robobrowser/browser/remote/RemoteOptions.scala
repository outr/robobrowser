package com.outr.robobrowser.browser.remote

import org.openqa.selenium.Capabilities
import org.openqa.selenium.remote.FileDetector
import spice.net._

case class RemoteOptions(url: URL, capabilities: Capabilities, fileDetector: Option[FileDetector]) {
  def fileDetector(detector: FileDetector): RemoteOptions = copy(fileDetector = Some(detector))

  def url(url: URL): RemoteOptions = copy(url = url)

  def grid: RemoteOptions = url(url"http://localhost:4444/wd/hub")

  def create(): Remote = new Remote(url, capabilities, fileDetector)
}
