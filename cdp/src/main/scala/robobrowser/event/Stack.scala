package robobrowser.event

import fabric.rw.*

case class Stack(callFrames: List[CallFrame])

object Stack {
  implicit val rw: RW[Stack] = RW.gen
}