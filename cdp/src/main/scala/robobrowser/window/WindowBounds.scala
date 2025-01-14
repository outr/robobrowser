package robobrowser.window

import fabric.rw._

case class WindowBounds(left: Int, top: Int, width: Int, height: Int, windowState: String)

object WindowBounds {
  implicit val rw: RW[WindowBounds] = RW.gen
}