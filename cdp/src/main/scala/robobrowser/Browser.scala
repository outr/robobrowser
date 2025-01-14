package robobrowser

case class Browser(path: String, port: Int = 9222)

object Browser {
  val Chrome: Browser = Browser("/usr/bin/google-chrome")
  val Chromium: Browser = Browser("/usr/bin/chromium-browser")
  val Edge: Browser = Browser("/usr/bin/microsoft-edge")
  val Vivaldi: Browser = Browser("/usr/bin/vivaldi")
}