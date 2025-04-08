package robobrowser.event

import fabric.rw._

case class FrameSubtreeWillBeDetachedEvent(frameId: String) extends Event

object FrameSubtreeWillBeDetachedEvent {
  implicit val rw: RW[FrameSubtreeWillBeDetachedEvent] = RW.gen
}