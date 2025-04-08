package robobrowser.event

import fabric.rw._

case class PolicyUpdatedEvent() extends Event

object PolicyUpdatedEvent {
  implicit val rw: RW[PolicyUpdatedEvent] = RW.gen
}