package com.outr.robobrowser

case class Browser(value: String)

object Browser {
  lazy val Android: Browser = Browser("android")
  lazy val Chrome: Browser = Browser("Chrome")
  lazy val Samsung: Browser = Browser("Samsung")
}