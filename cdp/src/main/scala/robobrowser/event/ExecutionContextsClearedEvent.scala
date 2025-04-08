package robobrowser.event

import fabric.rw._

case class ExecutionContextsClearedEvent() extends Event

object ExecutionContextsClearedEvent {
  implicit val rw: RW[ExecutionContextsClearedEvent] = RW.gen
}