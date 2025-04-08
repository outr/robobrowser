package robobrowser.event

import fabric.rw._

case class Stack(callFrames: List[CallFrame])

object Stack {
  implicit val rw: RW[Stack] = RW.gen
}