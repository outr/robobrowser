package robobrowser.event

import fabric.rw._

case class NetworkRequest(url: String,
                          method: String,
                          headers: Map[String, String],
                          mixedContentType: Option[String],
                          initialPriority: String,
                          referrerPolicy: String,
                          isSameSite: Option[Boolean])

object NetworkRequest {
  implicit val rw: RW[NetworkRequest] = RW.gen
}