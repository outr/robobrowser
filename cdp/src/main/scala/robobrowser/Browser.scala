package robobrowser

import java.io.File
import java.net.ServerSocket

case class Browser(paths: List[String], port: Int = 0) {
  lazy val existingPaths: List[File] = paths.map(path => new File(path)).filter(_.exists())
  lazy val exists: Boolean = existingPaths.nonEmpty

  /** Returns a copy with a concrete port, finding a free one if port is 0. */
  def resolvePort(): Browser = if (port != 0) this else {
    val ss = new ServerSocket(0)
    try copy(port = ss.getLocalPort)
    finally ss.close()
  }
}

object Browser {
  val Chrome: Browser = Browser(List("/usr/bin/google-chrome"))
  val Chromium: Browser = Browser(List("/usr/bin/chromium-browser", "/usr/bin/chromium"))
  val Edge: Browser = Browser(List("/usr/bin/microsoft-edge"))
  val Vivaldi: Browser = Browser(List("/usr/bin/vivaldi"))

  lazy val all: List[Browser] = List(Chrome, Chromium, Edge, Vivaldi)

  def auto(available: List[Browser] = all): Browser = available
    .find(_.exists)
    .getOrElse(throw new RuntimeException(s"No available browser found: ${available.flatMap(_.paths).mkString(", ")}"))
}