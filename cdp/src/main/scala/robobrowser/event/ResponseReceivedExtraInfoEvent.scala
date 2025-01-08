package robobrowser.event

import fabric.rw._

case class ResponseReceivedExtraInfoEvent(requestId: String,
                                          blockedCookies: List[String],
                                          headers: Map[String, String],
                                          resourceIPAddressSpace: String,
                                          statusCode: Int,
                                          cookiePartitionKey: CookiePartitionKey,
                                          cookiePartitionKeyOpaque: Boolean,
                                          exemptedCookies: List[String]) extends Event

object ResponseReceivedExtraInfoEvent {
  implicit val rw: RW[ResponseReceivedExtraInfoEvent] = RW.gen
}