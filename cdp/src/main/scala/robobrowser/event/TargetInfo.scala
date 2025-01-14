package robobrowser.event

import fabric.rw._

case class TargetInfo(targetId: String,
                      `type`: String,
                      title: String,
                      url: String,
                      attached: Boolean,
                      canAccessOpener: Boolean,
                      browserContextId: String)

object TargetInfo {
  implicit val rw: RW[TargetInfo] = RW.gen
}
