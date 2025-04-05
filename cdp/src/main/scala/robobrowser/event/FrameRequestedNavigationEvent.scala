package robobrowser.event

import fabric.rw.*

case class FrameRequestedNavigationEvent(frameId: String,
                                         reason: String,
                                         url: String,
                                         disposition: String) extends Event

object FrameRequestedNavigationEvent {
  implicit val rw: RW[FrameRequestedNavigationEvent] = RW.gen
}