package com.outr.robobrowser

object iOS {
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