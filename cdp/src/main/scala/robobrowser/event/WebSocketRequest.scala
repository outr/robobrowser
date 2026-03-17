package robobrowser.event

import fabric.rw._

case class WebSocketRequest(headers: Map[String, String])

object WebSocketRequest {
  implicit val rw: RW[WebSocketRequest] = RW.gen
}
