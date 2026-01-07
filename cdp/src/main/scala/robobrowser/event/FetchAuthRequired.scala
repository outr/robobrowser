package robobrowser.event

import fabric.rw._
import robobrowser.fetch.{AuthChallenge, RequestId}

case class FetchAuthRequired(requestId: RequestId,
                             request: NetworkRequest,
                             frameId: String,
                             resourceType: String,
                             authChallenge: AuthChallenge) extends Event

object FetchAuthRequired {
  implicit val rw: RW[FetchAuthRequired] = RW.gen
}