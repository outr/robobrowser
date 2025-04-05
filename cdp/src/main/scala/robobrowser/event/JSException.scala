package robobrowser.event

import fabric.rw.*

case class JSException(`type`: String,
                       subtype: String,
                       className: String,
                       description: String,
                       objectId: String,
                       preview: JSExceptionPreview)

object JSException {
  implicit val rw: RW[JSException] = RW.gen
}