package robobrowser.event

import fabric.rw.RW

case class DocumentUpdatedEvent() extends Event

object DocumentUpdatedEvent {
  implicit val rw: RW[DocumentUpdatedEvent] = RW.gen
}