package robobrowser.event

import fabric.rw._

case class ConnectTiming(requestTime: Double)

object ConnectTiming {
  implicit val rw: RW[ConnectTiming] = RW.gen
}