package robobrowser.event

import fabric.rw._

case class WebSocketFrameErrorEvent(requestId: String,
                                    timestamp: Double,
                                    errorMessage: String) extends Event

object WebSocketFrameErrorEvent {
  implicit val rw: RW[WebSocketFrameErrorEvent] = RW.gen
}
