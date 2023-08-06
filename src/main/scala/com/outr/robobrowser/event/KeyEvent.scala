package com.outr.robobrowser.event

import fabric.rw._

case class KeyEvent(code: String,
                    key: String,
                    repeat: Boolean,
                    composing: Boolean,
                    shift: Boolean,
                    alt: Boolean,
                    ctrl: Boolean,
                    meta: Boolean)

object KeyEvent {
  implicit val rw: RW[KeyEvent] = RW.gen
}