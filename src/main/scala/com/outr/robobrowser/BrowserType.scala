package com.outr.robobrowser

case class BrowserType(value: String)

object BrowserType {
  lazy val Android: BrowserType = BrowserType("android")
  lazy val Chrome: BrowserType = BrowserType("Chrome")
  lazy val Samsung: BrowserType = BrowserType("Samsung")
}