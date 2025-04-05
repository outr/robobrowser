package robobrowser.event

import fabric.rw.*

case class DomContentEventFiredEvent(timestamp: Double) extends Event

object DomContentEventFiredEvent {
  implicit val rw: RW[DomContentEventFiredEvent] = RW.gen
}