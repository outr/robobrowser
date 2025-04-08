package robobrowser.event

import fabric.rw._

case class JSExceptionPreview(`type`: String,
                              subtype: String,
                              description: String,
                              overflow: Boolean,
                              properties: List[ExceptionProperty])

object JSExceptionPreview {
  implicit val rw: RW[JSExceptionPreview] = RW.gen
}