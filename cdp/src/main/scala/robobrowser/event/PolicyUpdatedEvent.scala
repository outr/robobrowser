package robobrowser.event

import fabric.rw.RW

case class PolicyUpdatedEvent() extends Event

object PolicyUpdatedEvent {
  implicit val rw: RW[PolicyUpdatedEvent] = RW.gen
}