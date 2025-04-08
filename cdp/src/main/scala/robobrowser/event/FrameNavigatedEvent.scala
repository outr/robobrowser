package robobrowser.event

import fabric.rw._

case class FrameNavigatedEvent(frame: Frame, `type`: String) extends Event

object FrameNavigatedEvent {
  implicit val rw: RW[FrameNavigatedEvent] = RW.gen
}