package robobrowser.event

import fabric.rw._

case class Cookie(name: String,
                  value: String,
                  domain: String,
                  path: String,
                  expires: Double,
                  size: Int,
                  httpOnly: Boolean,
                  secure: Boolean,
                  session: Boolean,
                  priority: String,
                  sameParty: Boolean,
                  sourceScheme: String,
                  sourcePort: Int)

object Cookie {
  implicit val rw: RW[Cookie] = RW.gen
}