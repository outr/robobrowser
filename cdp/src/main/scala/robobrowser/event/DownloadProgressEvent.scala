package robobrowser.event

import fabric.rw._

case class DownloadProgressEvent(guid: String,
                                 totalBytes: Long,
                                 receivedBytes: Long,
                                 state: String) extends Event

object DownloadProgressEvent {
  implicit val rw: RW[DownloadProgressEvent] = RW.gen
}