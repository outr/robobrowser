package robobrowser.event

import fabric.rw._

case class FrameClearedScheduledNavigationEvent(frameId: String) extends Event

object FrameClearedScheduledNavigationEvent {
  implicit val rw: RW[FrameClearedScheduledNavigationEvent] = RW.gen
}