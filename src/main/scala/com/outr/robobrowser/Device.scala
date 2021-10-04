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
}