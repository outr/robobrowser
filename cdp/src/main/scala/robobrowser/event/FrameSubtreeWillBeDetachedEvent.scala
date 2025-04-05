package robobrowser.event

import fabric.rw.*

case class FrameSubtreeWillBeDetachedEvent(frameId: String) extends Event

object FrameSubtreeWillBeDetachedEvent {
  implicit val rw: RW[FrameSubtreeWillBeDetachedEvent] = RW.gen
}