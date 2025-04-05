package robobrowser.event

import fabric.rw.*

case class ExceptionDetails(exceptionId: Int,
                            text: String,
                            lineNumber: Int,
                            columnNumber: Int,
                            scriptId: String,
                            url: String,
                            stackTrace: Stack,
                            exception: JSException,
                            executionContextId: Int)

object ExceptionDetails {
  implicit val rw: RW[ExceptionDetails] = RW.gen
}
