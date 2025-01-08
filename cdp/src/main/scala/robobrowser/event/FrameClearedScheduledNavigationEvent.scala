package robobrowser.event

import fabric.rw.RW

case class FrameClearedScheduledNavigationEvent(frameId: String) extends Event

object FrameClearedScheduledNavigationEvent {
  implicit val rw: RW[FrameClearedScheduledNavigationEvent] = RW.gen
}