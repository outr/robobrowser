package robobrowser.event

import fabric.rw.RW

case class DetachedEvent(reason: String) extends Event

object DetachedEvent {
  implicit val rw: RW[DetachedEvent] = RW.gen
}