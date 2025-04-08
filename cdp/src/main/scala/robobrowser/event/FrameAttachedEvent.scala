package robobrowser.event

import fabric.rw._

case class FrameAttachedEvent(frameId: String,
                              parentFrameId: String,
                              stack: Stack) extends Event

object FrameAttachedEvent {
  implicit val rw: RW[FrameAttachedEvent] = RW.gen
}