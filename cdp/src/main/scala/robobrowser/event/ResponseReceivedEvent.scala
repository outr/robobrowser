package robobrowser.event

import fabric.rw._

case class ResponseReceivedEvent(requestId: String,
                                 loaderId: String,
                                 timestamp: Double,
                                 `type`: String,
                                 response: NetworkResponse,
                                 hasExtraInfo: Boolean,
                                 frameId: Option[String]) extends Event

object ResponseReceivedEvent {
  implicit val rw: RW[ResponseReceivedEvent] = RW.gen
}