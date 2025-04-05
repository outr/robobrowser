package robobrowser.event

import fabric.rw._

case class AdFrameStatus(adFrameType: String)

object AdFrameStatus {
  implicit val rw: RW[AdFrameStatus] = RW.gen
}