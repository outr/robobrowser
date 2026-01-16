package robobrowser.comm

import fabric.{Json, Obj}
import fabric.rw._

case class WSRequest(id: Int,
                     method: String,
                     params: Json = Obj.empty,
                     sessionId: Option[String] = None)

object WSRequest {
  implicit val rw: RW[WSRequest] = RW.gen
}