package robobrowser.event

import fabric.rw._

case class WebSocketWillSendHandshakeRequestEvent(requestId: String,
                                                  timestamp: Double,
                                                  wallTime: Double,
                                                  request: WebSocketRequest) extends Event

object WebSocketWillSendHandshakeRequestEvent {
  implicit val rw: RW[WebSocketWillSendHandshakeRequestEvent] = RW.gen
}