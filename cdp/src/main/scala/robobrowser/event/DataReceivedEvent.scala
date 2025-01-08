package robobrowser.event

import fabric.rw._

case class DataReceivedEvent(requestId: String,
                             timestamp: Double,
                             dataLength: Long,
                             encodedDataLength: Long) extends Event

object DataReceivedEvent {
  implicit val rw: RW[DataReceivedEvent] = RW.gen
}