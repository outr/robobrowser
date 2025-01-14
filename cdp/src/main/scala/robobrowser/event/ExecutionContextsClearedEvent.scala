package robobrowser.event

import fabric.rw.RW

case class ExecutionContextsClearedEvent() extends Event

object ExecutionContextsClearedEvent {
  implicit val rw: RW[ExecutionContextsClearedEvent] = RW.gen
}