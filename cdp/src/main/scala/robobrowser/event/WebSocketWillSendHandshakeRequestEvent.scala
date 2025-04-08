package robobrowser.event

import fabric.rw._

case class WebSocketWillSendHandshakeRequestEvent(requestId: String,
                                                  timestamp: Double,
                                                  wallTime: Double,
                                                  request: NetworkRequest) extends Event

object WebSocketWillSendHandshakeRequestEvent {
  implicit val rw: RW[WebSocketWillSendHandshakeRequestEvent] = RW.gen
}