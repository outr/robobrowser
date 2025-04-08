package robobrowser.event

import fabric.rw._

case class FrameDetachedEvent(frameId: String, reason: String) extends Event

object FrameDetachedEvent {
  implicit val rw: RW[FrameDetachedEvent] = RW.gen
}