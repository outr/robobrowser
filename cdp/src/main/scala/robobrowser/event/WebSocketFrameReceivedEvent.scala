package robobrowser.event

import fabric.rw._

case class WebSocketFrameReceivedEvent(requestId: String,
                                       timestamp: Double,
                                       response: WebSocketFrame) extends Event

object WebSocketFrameReceivedEvent {
  implicit val rw: RW[WebSocketFrameReceivedEvent] = RW.gen
}
