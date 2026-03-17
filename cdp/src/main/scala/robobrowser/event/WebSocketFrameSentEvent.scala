package robobrowser.event

import fabric.rw._

case class WebSocketFrameSentEvent(requestId: String,
                                   timestamp: Double,
                                   response: WebSocketFrame) extends Event

object WebSocketFrameSentEvent {
  implicit val rw: RW[WebSocketFrameSentEvent] = RW.gen
}
