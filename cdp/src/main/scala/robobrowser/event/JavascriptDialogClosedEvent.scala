package robobrowser.event

import fabric.rw.RW

case class JavascriptDialogClosedEvent(result: Boolean,
                                       userInput: String) extends Event

object JavascriptDialogClosedEvent {
  implicit val rw: RW[JavascriptDialogClosedEvent] = RW.gen
}