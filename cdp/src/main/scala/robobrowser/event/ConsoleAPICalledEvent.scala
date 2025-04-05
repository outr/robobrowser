package robobrowser.event

import fabric.rw.*

case class ConsoleAPICalledEvent(`type`: String,
                                 args: List[ConsoleArg],
                                 executionContextId: Int,
                                 timestamp: Double,
                                 stackTrace: Stack) extends Event

object ConsoleAPICalledEvent {
  implicit val rw: RW[ConsoleAPICalledEvent] = RW.gen
}