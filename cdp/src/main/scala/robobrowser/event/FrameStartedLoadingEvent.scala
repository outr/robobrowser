package robobrowser.event

import fabric.rw.*

case class FrameStartedLoadingEvent(frameId: String) extends Event

object FrameStartedLoadingEvent {
  implicit val rw: RW[FrameStartedLoadingEvent] = RW.gen
}