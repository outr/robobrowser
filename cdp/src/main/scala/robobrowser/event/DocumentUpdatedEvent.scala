package robobrowser.event

import fabric.rw.*

case class DocumentUpdatedEvent() extends Event

object DocumentUpdatedEvent {
  implicit val rw: RW[DocumentUpdatedEvent] = RW.gen
}