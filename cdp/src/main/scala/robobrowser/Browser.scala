package robobrowser

import java.io.File

case class Browser(paths: List[String], port: Int = 9222) {
  lazy val existingPaths: List[File] = paths.map(path => new File(path)).filter(_.exists())
  lazy val exists: Boolean = existingPaths.nonEmpty
}

object Browser {
  val Chrome: Browser = Browser(List("/usr/bin/google-chrome"))
  val Chromium: Browser = Browser(List("/usr/bin/chromium-browser", "/usr/bin/chromium"))
  val Edge: Browser = Browser(List("/usr/bin/microsoft-edge"))
  val Vivaldi: Browser = Browser(List("/usr/bin/vivaldi"))

  lazy val all: List[Browser] = List(Chromium, Chrome, Edge, Vivaldi)

  def auto(available: List[Browser] = all): Browser = available
    .find(_.exists)
    .getOrElse(throw new RuntimeException(s"No available browser found: ${available.flatMap(_.paths).mkString(", ")}"))
}