package robobrowser.event

import fabric.rw.*

case class ExecutionContextsClearedEvent() extends Event

object ExecutionContextsClearedEvent {
  implicit val rw: RW[ExecutionContextsClearedEvent] = RW.gen
}