package robobrowser.window

import fabric.rw._

case class Window(windowId: Long, bounds: WindowBounds)

object Window {
  implicit val rw: RW[Window] = RW.gen
}