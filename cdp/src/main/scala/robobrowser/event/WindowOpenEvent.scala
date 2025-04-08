package robobrowser.event

import fabric.rw._

case class WindowOpenEvent(url: String,
                           windowName: String,
                           windowFeatures: List[String],
                           userGesture: Boolean) extends Event

object WindowOpenEvent {
  implicit val rw: RW[WindowOpenEvent] = RW.gen
}
