package robobrowser.comm

import fabric.rw._

case class ResponseError(code: Int, message: String)

object ResponseError {
  implicit val rw: RW[ResponseError] = RW.gen
}