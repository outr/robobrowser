package com.outr.robobrowser


case class Device(screenSize: Option[ScreenSize] = None,
                  identifier: Option[String] = None,
                  osVersion: Option[String] = None,
                  browserName: Option[String] = None,
                  realMobile: Option[Boolean] = None,
                  language: String = "en-US",
                  userAgent: Option[String] = None,
                  emulateMobile: Boolean = false)

object Device {
  lazy val Chrome: Device = Device()
  lazy val Pixel2XL: Device = Device(
    screenSize = Some(ScreenSize(412, 823)),
    userAgent = Some("Mozilla/5.0 (Linux; Android 11; Pixel 2 XL Build/RP1A.201005.004; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/86.0.4240.185 Mobile Safari/537.36 GSA/11.35.6.23.arm64"),
    emulateMobile = true
  )
  lazy val iPhone11Pro: Device = Device(
    identifier = Some("iPhone 11 Pro"),
    osVersion = Some("13"),
    browserName = Some("ios"),
    realMobile = Some(true)
  )
  lazy val iPhone12ProMax: Device = Device(
    identifier = Some("iPhone 12 Pro Max"),
    osVersion = Some("14"),
    browserName = Some("ios"),
    realMobile = Some(true)
  )
}