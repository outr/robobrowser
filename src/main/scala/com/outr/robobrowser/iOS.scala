package com.outr.robobrowser

object iOS {
  def apply(id: String, os: Version, realMobile: Boolean = true): Device = Device(
    identifier = Some(id),
    osVersion = Some(os.value),
    browserName = Some("ios"),
    realMobile = Some(realMobile)
  )

  lazy val v12: Version = Version("12")
  lazy val v13: Version = Version("13")
  lazy val v14: Version = Version("14")
  lazy val v15: Version = Version("15")

  object iPhone10 {
    def XSMax(os: Version = v12): Device = iOS("iPhone XS Max", os)
    def XS(os: Version = v15): Device = iOS("iPhone XS", os)
    def apply(os: Version = v12): Device = iOS("iPhone X", os)
  }
  object iPhone11 {
    def Pro(os: Version = v13): Device = iOS("iPhone 11 Pro", os)
  }
  object iPhone12 {
    def ProMax(os: Version = v14): Device = iOS("iPhone 12 Pro Max", os)
  }

  case class Version(value: String)
}