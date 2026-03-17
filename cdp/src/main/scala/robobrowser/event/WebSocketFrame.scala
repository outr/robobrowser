package robobrowser.event

import fabric.rw._

case class WebSocketFrame(opcode: Int,
                          mask: Boolean,
                          payloadData: String)

object WebSocketFrame {
  implicit val rw: RW[WebSocketFrame] = RW.gen
}
