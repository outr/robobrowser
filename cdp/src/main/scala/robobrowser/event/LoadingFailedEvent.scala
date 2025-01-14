package robobrowser.event

import fabric.rw._

case class LoadingFailedEvent(requestId: String,
                              timestamp: Double,
                              `type`: String,
                              errorText: String,
                              canceled: Boolean) extends Event

object LoadingFailedEvent {
  implicit val rw: RW[LoadingFailedEvent] = RW.gen
}