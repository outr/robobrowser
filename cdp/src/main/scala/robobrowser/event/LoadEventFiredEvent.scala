package robobrowser.event

import fabric.rw.RW

case class LoadEventFiredEvent(timestamp: Double) extends Event

object LoadEventFiredEvent {
  implicit val rw: RW[LoadEventFiredEvent] = RW.gen
}