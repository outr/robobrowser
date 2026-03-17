package robobrowser.event

import fabric.rw._

case class WebSocketClosedEvent(requestId: String,
                                timestamp: Double) extends Event

object WebSocketClosedEvent {
  implicit val rw: RW[WebSocketClosedEvent] = RW.gen
}
