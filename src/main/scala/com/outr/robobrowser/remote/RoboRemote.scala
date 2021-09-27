package com.outr.robobrowser.remote

import com.outr.robobrowser.RoboBrowser
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver

class RoboRemote(override val options: RemoteOptions = RemoteOptions()) extends RoboBrowser {
  override protected def createWebDriver(options: ChromeOptions): WebDriver = {
    val url = new java.net.URL(this.options.url.toString())
    new RemoteWebDriver(url, options)
  }
}