package robobrowser.event

import fabric.rw._

case class AssociatedCookie(cookie: Cookie, blockedReasons: List[String], exemptionReason: String)

object AssociatedCookie {
  implicit val rw: RW[AssociatedCookie] = RW.gen
}