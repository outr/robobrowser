package robobrowser

import java.io.File

case class Browser(path: String, port: Int = 9222) {
  lazy val exists: Boolean = new File(path).isFile
}

object Browser {
  val Chrome: Browser = Browser("/usr/bin/google-chrome")
  val Chromium: Browser = Browser("/usr/bin/chromium-browser")
  val Edge: Browser = Browser("/usr/bin/microsoft-edge")
  val Vivaldi: Browser = Browser("/usr/bin/vivaldi")

  lazy val all: List[Browser] = List(Chrome, Chromium, Edge, Vivaldi)

  def auto(available: List[Browser] = all): Browser = available
    .find(_.exists)
    .getOrElse(throw new RuntimeException(s"No available browser found: ${available.map(_.path).mkString(", ")}"))
}