package robobrowser.event

import fabric.rw.RW

case class DetachedFromTargetEvent(sessionId: String, targetId: String) extends Event

object DetachedFromTargetEvent {
  implicit val rw: RW[DetachedFromTargetEvent] = RW.gen
}