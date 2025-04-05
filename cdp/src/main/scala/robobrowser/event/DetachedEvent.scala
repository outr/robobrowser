package robobrowser.event

import fabric.rw.*

case class DetachedEvent(reason: String) extends Event

object DetachedEvent {
  implicit val rw: RW[DetachedEvent] = RW.gen
}