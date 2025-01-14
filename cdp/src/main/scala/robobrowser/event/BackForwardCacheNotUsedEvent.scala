package robobrowser.event

import fabric.Json
import fabric.rw.RW

case class BackForwardCacheNotUsedEvent(loaderId: String,
                                        frameId: String,
                                        notRestoredExplanations: Json,
                                        notRestoredExplanationsTree: Json) extends Event

object BackForwardCacheNotUsedEvent {
  implicit val rw: RW[BackForwardCacheNotUsedEvent] = RW.gen
}