package robobrowser.event

import fabric.rw._

case class DetachedFromTargetEvent(sessionId: String, targetId: String) extends Event

object DetachedFromTargetEvent {
  implicit val rw: RW[DetachedFromTargetEvent] = RW.gen
}