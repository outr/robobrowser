package robobrowser.event

import fabric.rw._

case class LifecycleEvent(frameId: String,
                          loaderId: String,
                          name: String,
                          timestamp: Double) extends Event

object LifecycleEvent {
  implicit val rw: RW[LifecycleEvent] = RW.gen
}