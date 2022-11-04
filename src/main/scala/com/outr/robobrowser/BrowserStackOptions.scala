package com.outr.robobrowser

import spice.net.URL

case class BrowserStackOptions(username: String,
                               automateKey: String,
                               projectName: String,
                               buildName: String,
                               sessionName: Option[String] = None,
                               consoleLogs: String = "info",
                               networkLogs: Boolean = true,
                               idleTimeout: Int = 300,
                               local: Boolean = false,
                               appiumVersion: String = "1.22.0") {
  lazy val url: URL = BrowserStack.url(username, automateKey)
}