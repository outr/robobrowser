package robobrowser.event

import fabric.rw.*

case class CallFrame(functionName: String,
                     scriptId: String,
                     url: String,
                     lineNumber: Int,
                     columnNumber: Int)

object CallFrame {
  implicit val rw: RW[CallFrame] = RW.gen
}