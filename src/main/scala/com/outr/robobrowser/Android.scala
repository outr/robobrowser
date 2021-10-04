package com.outr.robobrowser

object Android {
  lazy val Pixel5: Device = Device(
    identifier = Some("Google Pixel 5"),
    osVersion = Some("12 Beta"),
    browserName = Some("android"),
    realMobile = Some(true)
  )
  lazy val Pixel2XL: Device = Device(
    screenSize = Some(ScreenSize(412, 823)),
    userAgent = Some("Mozilla/5.0 (Linux; Android 11; Pixel 2 XL Build/RP1A.201005.004; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/86.0.4240.185 Mobile Safari/537.36 GSA/11.35.6.23.arm64"),
    emulateMobile = true
  )
  lazy val SamsungGalaxyS21Ultra: Device = Device(
    identifier = Some("Samsung Galaxy S21 Ultra"),
    osVersion = Some("11.0"),
    browserName = Some("android"),
    realMobile = Some(true)
  )
}