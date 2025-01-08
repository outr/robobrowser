package robobrowser.event

import fabric.rw._

case class LoadingFinishedEvent(requestId: String,
                                timestamp: Double,
                                encodedDataLength: Long) extends Event

object LoadingFinishedEvent {
  implicit val rw: RW[LoadingFinishedEvent] = RW.gen
}