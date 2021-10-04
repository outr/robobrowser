package com.outr.robobrowser

import io.youi.net._
import org.openqa.selenium.chrome.ChromeOptions

trait BrowserStack extends RoboBrowser {
  protected def browserStackConsole: String = "errors"
  protected def browserStackNetworkLogs: Boolean = true
  protected def browserStackName: String = "RoboBrowser Test"
  protected def browserStackBuild: String = "Default Build"

  override protected def configureOptions(options: ChromeOptions): Unit = {
    super.configureOptions(options)

    options.setCapability("browserstack.console", browserStackConsole)
    options.setCapability("browserstack.networkLogs", browserStackNetworkLogs.toString)
    options.setCapability("name", browserStackName)
    options.setCapability("build", browserStackBuild)
  }
}

object BrowserStack {
  def url(username: String, automateKey: String): URL = URL(
    protocol = Protocol.Https,
    host = s"$username:$automateKey@hub-cloud.browserstack.com",
    port = 443,
    path = path"/wd/hub"
  )
}