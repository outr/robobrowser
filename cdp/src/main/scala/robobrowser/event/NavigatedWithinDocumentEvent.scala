package robobrowser.event

import fabric.rw._

case class NavigatedWithinDocumentEvent(frameId: String,
                                        url: String,
                                        navigationType: String) extends Event

object NavigatedWithinDocumentEvent {
  implicit val rw: RW[NavigatedWithinDocumentEvent] = RW.gen
}