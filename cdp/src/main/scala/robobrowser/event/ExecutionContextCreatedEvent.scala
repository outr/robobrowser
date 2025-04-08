package robobrowser.event

import fabric.rw._

case class ExecutionContextCreatedEvent(context: ExecutionContext) extends Event

object ExecutionContextCreatedEvent {
  implicit val rw: RW[ExecutionContextCreatedEvent] = RW.gen
}