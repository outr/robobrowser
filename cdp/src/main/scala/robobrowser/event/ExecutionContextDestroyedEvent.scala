package robobrowser.event

import fabric.rw.RW

case class ExecutionContextDestroyedEvent(executionContextId: Int, executionContextUniqueId: String) extends Event

object ExecutionContextDestroyedEvent {
  implicit val rw: RW[ExecutionContextDestroyedEvent] = RW.gen
}