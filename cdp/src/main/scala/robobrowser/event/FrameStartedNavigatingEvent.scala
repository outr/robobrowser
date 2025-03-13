package robobrowser.event

import fabric.rw.RW

case class FrameStartedNavigatingEvent(frameId: String, url: String, loaderId: String, navigationType: String) extends Event

object FrameStartedNavigatingEvent {
  implicit val rw: RW[FrameStartedNavigatingEvent] = RW.gen
}