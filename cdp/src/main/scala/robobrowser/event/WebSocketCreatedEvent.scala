package robobrowser.event

import fabric.rw.RW

case class WebSocketCreatedEvent(requestId: String,
                                 url: String,
                                 initiator: Initiator) extends Event

object WebSocketCreatedEvent {
  implicit val rw: RW[WebSocketCreatedEvent] = RW.gen
}