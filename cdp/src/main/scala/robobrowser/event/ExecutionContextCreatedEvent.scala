package robobrowser.event

import fabric.rw.RW

case class ExecutionContextCreatedEvent(context: ExecutionContext) extends Event

object ExecutionContextCreatedEvent {
  implicit val rw: RW[ExecutionContextCreatedEvent] = RW.gen
}