package robobrowser.comm

import fabric.Obj
import fabric.rw._

// {"id":9,"error":{"code":-32602,"message":"Either objectId or executionContextId or uniqueContextId must be specified"}}
case class WSResponse(id: Option[Int],
                      result: Obj = Obj.empty,
                      method: Option[String],
                      params: Obj = Obj.empty,
                      error: Option[ResponseError])

object WSResponse {
  implicit val rw: RW[WSResponse] = RW.gen
}