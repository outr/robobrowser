package robobrowser.event

import fabric.rw.*

case class FrameAttachedEvent(frameId: String,
                              parentFrameId: String,
                              stack: Stack) extends Event

object FrameAttachedEvent {
  implicit val rw: RW[FrameAttachedEvent] = RW.gen
}