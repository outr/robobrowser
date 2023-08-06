package com.outr.robobrowser.event

import fabric.rw._

case class PointerEvent(button: Int,
                        buttons: Int,
                        x: Int,
                        y: Int,
                        clientX: Int,
                        clientY: Int,
                        movementX: Int,
                        movementY: Int,
                        offsetX: Int,
                        offsetY: Int,
                        pageX: Int,
                        pageY: Int,
                        screenX: Int,
                        screenY: Int,
                        pointerId: Int,
                        width: Int,
                        height: Int,
                        pressure: Double,
                        tangentialPressure: Double,
                        tiltX: Double,
                        tiltY: Double,
                        twist: Double,
                        pointerType: String,
                        primary: Boolean,
                        shift: Boolean,
                        alt: Boolean,
                        ctrl: Boolean,
                        meta: Boolean)

object PointerEvent {
  implicit val rw: RW[PointerEvent] = RW.gen
}