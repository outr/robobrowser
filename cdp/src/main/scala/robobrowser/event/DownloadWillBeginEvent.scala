package robobrowser.event

import fabric.rw.RW

case class DownloadWillBeginEvent(frameId: String,
                                  guid: String,
                                  url: String,
                                  suggestedFilename: String) extends Event

object DownloadWillBeginEvent {
  implicit val rw: RW[DownloadWillBeginEvent] = RW.gen
}