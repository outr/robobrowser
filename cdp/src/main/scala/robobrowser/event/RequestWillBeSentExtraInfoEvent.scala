package robobrowser.event

import fabric.rw._

case class RequestWillBeSentExtraInfoEvent(requestId: String,
                                           associatedCookies: List[AssociatedCookie],
                                           headers: Map[String, String],
                                           connectTiming: ConnectTiming,
                                           siteHasCookieInOtherPartition: Boolean) extends Event

object RequestWillBeSentExtraInfoEvent {
  implicit val rw: RW[RequestWillBeSentExtraInfoEvent] = RW.gen
}