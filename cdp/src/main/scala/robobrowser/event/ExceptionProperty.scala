package robobrowser.event

import fabric.rw.RW

case class ExceptionProperty(name: String, `type`: String, value: String)

object ExceptionProperty {
  implicit val rw: RW[ExceptionProperty] = RW.gen
}