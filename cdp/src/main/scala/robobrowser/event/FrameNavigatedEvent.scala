package robobrowser.event

import fabric.rw.RW

case class FrameNavigatedEvent(frame: Frame, `type`: String) extends Event

object FrameNavigatedEvent {
  implicit val rw: RW[FrameNavigatedEvent] = RW.gen
}