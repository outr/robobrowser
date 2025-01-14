package robobrowser.event

import fabric.rw.RW

case class FrameDetachedEvent(frameId: String, reason: String) extends Event

object FrameDetachedEvent {
  implicit val rw: RW[FrameDetachedEvent] = RW.gen
}