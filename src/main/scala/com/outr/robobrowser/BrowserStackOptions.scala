package com.outr.robobrowser

import io.youi.net.URL

case class BrowserStackOptions(username: String,
                               automateKey: String,
                               projectName: String,
                               buildName: String,
                               sessionName: Option[String] = None,
                               consoleLogs: String = "info",
                               networkLogs: Boolean = true,
                               idleTimeout: Int = 300,
                               local: Boolean = false,
                               appiumVersion: String = "1.22.0") extends Transient {
  lazy val url: URL = BrowserStack.url(username, automateKey)
}