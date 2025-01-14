package robobrowser.event

import fabric.rw._

case class RequestServedFromCacheEvent(requestId: String) extends Event

object RequestServedFromCacheEvent {
  implicit val rw: RW[RequestServedFromCacheEvent] = RW.gen
}