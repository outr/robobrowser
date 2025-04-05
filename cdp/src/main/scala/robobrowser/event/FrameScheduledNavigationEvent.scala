package robobrowser.event

import fabric.rw.*

case class FrameScheduledNavigationEvent(frameId: String,
                                         delay: Long,
                                         reason: String,
                                         url: String) extends Event

object FrameScheduledNavigationEvent {
  implicit val rw: RW[FrameScheduledNavigationEvent] = RW.gen
}