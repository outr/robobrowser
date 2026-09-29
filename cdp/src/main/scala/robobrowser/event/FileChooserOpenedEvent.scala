package robobrowser.event

import fabric.rw._

/** A page opened a file chooser while [[robobrowser.FileUpload.interceptChooser]] was on. `backendNodeId` is the file
  * input that opened it — absent when the page asked through the File System Access API, which has no input to fill.
  * `mode` is `selectSingle` or `selectMultiple`. */
case class FileChooserOpenedEvent(frameId: String,
                                  mode: String,
                                  backendNodeId: Option[Int] = None) extends Event

object FileChooserOpenedEvent {
  implicit val rw: RW[FileChooserOpenedEvent] = RW.gen
}
