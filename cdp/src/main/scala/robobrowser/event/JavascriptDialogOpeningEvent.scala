package robobrowser.event

import fabric.rw.*

case class JavascriptDialogOpeningEvent(url: String,
                                        message: String,
                                        `type`: String,
                                        hasBrowserHandler: Boolean,
                                        defaultPrompt: String) extends Event

object JavascriptDialogOpeningEvent {
  implicit val rw: RW[JavascriptDialogOpeningEvent] = RW.gen
}