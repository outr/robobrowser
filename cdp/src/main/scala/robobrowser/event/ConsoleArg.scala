package robobrowser.event

import fabric.rw._

case class ConsoleArg(`type`: String, value: String)

object ConsoleArg {
  implicit val rw: RW[ConsoleArg] = RW.gen
}