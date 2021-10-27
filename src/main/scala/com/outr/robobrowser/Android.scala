package com.outr.robobrowser

object Android {
  def apply(id: String,
            os: Version,
            browser: Browser = Browser.Android,
            realMobile: Boolean = true): Device = Device(
    identifier = Some(id),
    osVersion = Some(os.value),
    browserName = Some(browser.value),
    browser = Some(browser.value),
    realMobile = Some(realMobile)
  )

  lazy val v5: Version = Version("5.0")
  lazy val v6: Version = Version("6.0")
  lazy val v7: Version = Version("7.0")
  lazy val v7_1: Version = Version("7.1")
  lazy val v8: Version = Version("8.0")
  lazy val v8_1: Version = Version("8.1")
  lazy val v9: Version = Version("9.0")
  lazy val v10: Version = Version("10.0")
  lazy val v11: Version = Version("11.0")
  lazy val v12Beta: Version = Version("12 Beta")
  lazy val v12: Version = Version("12.0")

  object Pixel2 {
    def XL(os: Version = v11, browser: Browser = Browser.Android): Device = Android("Google Pixel 2 XL", os, browser)
  }
  object Pixel5 {
    def apply(os: Version = v12Beta, browser: Browser = Browser.Android): Device = Android("Google Pixel 5", os, browser)
  }
  object Samsung {
    def S6(os: Version = v5, browser: Browser = Browser.Android): Device = Android("Samsung Galaxy S6", os, browser)
    def S7(os: Version = v6, browser: Browser = Browser.Android): Device = Android("Samsung Galaxy S7", os, browser)
    def S8(os: Version = v7, browser: Browser = Browser.Android): Device = Android("Samsung Galaxy S8", os, browser)
    def S9(os: Version = v8, browser: Browser = Browser.Android): Device = Android("Samsung Galaxy S9", os, browser)
    def S10(os: Version = v9, browser: Browser = Browser.Android): Device = Android("Samsung Galaxy S10", os, browser)
    def S20(os: Version = v10, browser: Browser = Browser.Android): Device = Android("Samsung Galaxy S20", os, browser)
    object S21 {
      def apply(os: Version = v11, browser: Browser = Browser.Android): Device =
        Android("Samsung Galaxy S21", os, browser)

      def Ultra(os: Version = v11, browser: Browser = Browser.Android): Device =
        Android("Samsung Galaxy S21 Ultra", os, browser)
    }
  }

  case class Version(value: String)
  case class Browser(value: String)
  object Browser {
    lazy val Android: Browser = Browser("android")
    lazy val Chrome: Browser = Browser("Chrome")
    lazy val Samsung: Browser = Browser("Samsung")
  }
}