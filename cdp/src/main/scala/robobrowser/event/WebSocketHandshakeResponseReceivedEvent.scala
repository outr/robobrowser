package robobrowser.event

import fabric.rw._

case class WebSocketHandshakeResponseReceivedEvent(requestId: String,
                                                   timestamp: Double,
                                                   response: WebSocketHandshakeResponse) extends Event

object WebSocketHandshakeResponseReceivedEvent {
  implicit val rw: RW[WebSocketHandshakeResponseReceivedEvent] = RW.gen
}
