package robobrowser.event

import fabric.rw._

case class FrameResizedEvent() extends Event

object FrameResizedEvent {
  implicit val rw: RW[FrameResizedEvent] = RW.gen
}