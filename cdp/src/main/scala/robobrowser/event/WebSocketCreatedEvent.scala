package robobrowser.event

import fabric.rw._

case class WebSocketCreatedEvent(requestId: String,
                                 url: String,
                                 initiator: Initiator) extends Event

object WebSocketCreatedEvent {
  implicit val rw: RW[WebSocketCreatedEvent] = RW.gen
}