package robobrowser.event

import fabric.rw.*

case class ExceptionThrownEvent(timestamp: Double,
                                exceptionDetails: ExceptionDetails) extends Event

object ExceptionThrownEvent {
  implicit val rw: RW[ExceptionThrownEvent] = RW.gen
}