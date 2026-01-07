package robobrowser.event

import fabric.rw._
import robobrowser.fetch.{AuthChallenge, RequestId}
import spice.http.Headers

case class FetchRequestPausedEvent(requestId: RequestId,
                                   request: NetworkRequest,
                                   frameId: String,
                                   resourceType: String,
                                   requestHeaders: List[HeaderEntry] = Nil,
                                   responseHeaders: List[HeaderEntry] = Nil,
                                   responseStatusCode: Option[Int],
                                   responseErrorReason: Option[String],
                                   networkId: Option[String]) extends Event {
  object headers {
    lazy val request: Headers = Headers(requestHeaders.map(e => e.name -> List(e.value)).toMap)
    lazy val response: Headers = Headers(responseHeaders.map(e => e.name -> List(e.value)).toMap)
  }
}

object FetchRequestPausedEvent {
  implicit val rw: RW[FetchRequestPausedEvent] = RW.gen
}