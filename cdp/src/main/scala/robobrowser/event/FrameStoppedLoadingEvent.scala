package robobrowser.event

import fabric.rw.*

case class FrameStoppedLoadingEvent(frameId: String) extends Event

object FrameStoppedLoadingEvent {
  implicit val rw: RW[FrameStoppedLoadingEvent] = RW.gen
}