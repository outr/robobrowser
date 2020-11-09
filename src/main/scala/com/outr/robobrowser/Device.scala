package com.outr.robobrowser

import com.machinepublishers.jbrowserdriver.UserAgent

case class Device(screenSize: (Int, Int) = (1920, 1080),
                  language: String = "en-US",
                  userAgentString: String = "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.85 Safari/537.36",
                  appVersion: String = "5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.85 Safari/537.36",
                  oscpu: String = "Windows NT 6.1",
                  platform: String = "Win32",
                  vendor: String = "Google, Inc.",
                  family: UserAgent.Family = UserAgent.Family.WEBKIT) {
  lazy val (width: Int, height: Int) = screenSize
  lazy val userAgent: UserAgent = new UserAgent(family, language, vendor, platform, oscpu, appVersion, userAgentString)
}

object Device {
  lazy val Chrome: Device = Device()
  lazy val Pixel2XL: Device = Device(
    screenSize = (412, 823),
    userAgentString = "Mozilla/5.0 (Linux; Android 11; Pixel 2 XL Build/RP1A.201005.004; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/86.0.4240.185 Mobile Safari/537.36 GSA/11.35.6.23.arm64",
    appVersion = "5.0 (Linux; Android 11; Pixel 2 XL) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.185 Mobile Safari/537.36",
    oscpu = null,
    platform = "Linux armv8l"
  )
}