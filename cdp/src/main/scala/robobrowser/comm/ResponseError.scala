package robobrowser.comm

import fabric.rw.RW

case class ResponseError(code: Int, message: String)

object ResponseError {
  implicit val rw: RW[ResponseError] = RW.gen
}