package robobrowser.event

import fabric.rw._

case class ResourceChangedPriorityEvent(requestId: String, newPriority: String, timestamp: Double) extends Event

object ResourceChangedPriorityEvent {
  implicit val rw: RW[ResourceChangedPriorityEvent] = RW.gen
}