package robobrowser.event

import fabric.rw._

case class HeaderEntry(name: String,
                       value: String)

object HeaderEntry {
  implicit val rw: RW[HeaderEntry] = RW.gen
}


