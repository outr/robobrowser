package robobrowser.comm

import fabric.Obj
import fabric.rw._

case class WSRequest(id: Int,
                     method: String,
                     params: Obj = Obj.empty,
                     sessionId: Option[String] = None)

object WSRequest {
  implicit val rw: RW[WSRequest] = RW.gen
}