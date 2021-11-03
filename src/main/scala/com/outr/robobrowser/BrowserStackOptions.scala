package com.outr.robobrowser

case class BrowserStackOptions(username: String,
                               automateKey: String,
                               projectName: String,
                               buildName: String,
                               sessionName: Option[String] = None,
                               consoleLogs: String = "errors",
                               networkLogs: Boolean = true,
                               idleTimeout: Int = 300,
                               local: Boolean = false,
                               appiumVersion: String = "1.21.0")