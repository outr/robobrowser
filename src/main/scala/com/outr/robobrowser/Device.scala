package com.outr.robobrowser


case class Device(screenSize: (Int, Int) = (1920, 1080),
                  language: String = "en-US",
                  userAgent: Option[String] = None,
                  emulateMobile: Boolean = false) {
  lazy val (width: Int, height: Int) = screenSize
}

object Device {
  lazy val Chrome: Device = Device()
  lazy val Pixel2XL: Device = Device(
    screenSize = (412, 823),
    userAgent = Some("Mozilla/5.0 (Linux; Android 11; Pixel 2 XL Build/RP1A.201005.004; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/86.0.4240.185 Mobile Safari/537.36 GSA/11.35.6.23.arm64"),
    emulateMobile = true
  )
}