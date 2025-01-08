package robobrowser.event

import fabric.rw._

case class RequestWillBeSentEvent(requestId: String,
                                  loaderId: String,
                                  documentURL: String,
                                  request: NetworkRequest,
                                  timestamp: Double,
                                  wallTime: Double,
                                  initiator: Initiator,
                                  redirectHasExtraInfo: Boolean,
                                  `type`: String,
                                  frameId: Option[String],
                                  hasUserGesture: Boolean) extends Event

object RequestWillBeSentEvent {
  implicit val rw: RW[RequestWillBeSentEvent] = RW.gen
}