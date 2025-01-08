package robobrowser.event

import fabric.rw._

case class CookiePartitionKey(topLevelSite: String, hasCrossSiteAncestor: Boolean)

object CookiePartitionKey {
  implicit val rw: RW[CookiePartitionKey] = RW.gen
}