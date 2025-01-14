package robobrowser.event

import fabric.rw._

case class Initiator(`type`: String,
                     stack: Option[Stack],
                     url: Option[String],
                     lineNumber: Option[Int],
                     columnNumber: Option[Int])

object Initiator {
  implicit val rw: RW[Initiator] = RW.gen
}