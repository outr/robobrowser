package robobrowser.comm

import fabric.Obj
import fabric.rw._

case class WSResponse(id: Option[Int],
                      result: Obj = Obj.empty,
                      method: Option[String],
                      params: Obj = Obj.empty)

object WSResponse {
  implicit val rw: RW[WSResponse] = RW.gen
}