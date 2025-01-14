package robobrowser.event

import fabric.rw._

case class AttachedToTargetEvent(sessionId: String, targetInfo: TargetInfo, waitingForDebugger: Boolean) extends Event

object AttachedToTargetEvent {
  implicit val rw: RW[AttachedToTargetEvent] = RW.gen
}