package robobrowser.event

import fabric.rw.RW

case class ResponseBody(body: String, base64Encoded: Boolean)

object ResponseBody {
  implicit val rw: RW[ResponseBody] = RW.gen
}