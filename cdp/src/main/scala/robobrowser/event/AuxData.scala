package robobrowser.event

import fabric.rw._

case class AuxData(isDefault: Boolean, `type`: String, frameId: String)

object AuxData {
  implicit val rw: RW[AuxData] = RW.gen
}