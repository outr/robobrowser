package robobrowser.stream

import fabric._
import fabric.define.{DefType, Definition}
import fabric.dsl._
import fabric.rw._

/** Input events sent by the viewer over the stream's DataChannel, mapped by
  * [[InputRouter]] onto CDP input dispatch. Field names are chosen so the
  * JavaScript client can populate them straight from DOM events; the wire
  * format is `"type"`-discriminated and stable:
  *
  * {{{
  * {"type":"mousemove","x":1.5,"y":2.5,"buttons":1}
  * {"type":"mousedown","x":1,"y":2,"button":"left","buttons":1,"clickCount":1,"modifiers":0}
  * {"type":"mouseup","x":1,"y":2,"button":"left","buttons":0,"clickCount":1,"modifiers":0}
  * {"type":"wheel","x":1,"y":2,"deltaX":0,"deltaY":120,"modifiers":0}
  * {"type":"keydown","key":"a","code":"KeyA","keyCode":65,"text":"","modifiers":0}
  * {"type":"keyup","key":"a","code":"KeyA","keyCode":65,"text":"","modifiers":0}
  * {"type":"char","text":"a","key":"a","keyCode":65}
  * }}}
  *
  * Coordinates are stream pixels (the video's intrinsic pixel space); the
  * server owns all scaling back to viewport CSS pixels. `modifiers` is the CDP
  * bitmask (alt=1, ctrl=2, meta=4, shift=8), computed client-side. */
sealed trait InputMessage

object InputMessage {
  case class MouseMove(x: Double, y: Double, buttons: Int) extends InputMessage
  case class MouseDown(x: Double, y: Double, button: String, buttons: Int, clickCount: Int, modifiers: Int) extends InputMessage
  case class MouseUp(x: Double, y: Double, button: String, buttons: Int, clickCount: Int, modifiers: Int) extends InputMessage
  case class Wheel(x: Double, y: Double, deltaX: Double, deltaY: Double, modifiers: Int) extends InputMessage
  case class KeyDown(key: String, code: String, keyCode: Int, text: String, modifiers: Int) extends InputMessage
  case class KeyUp(key: String, code: String, keyCode: Int, text: String, modifiers: Int) extends InputMessage
  case class Char(text: String, key: String, keyCode: Int) extends InputMessage

  private def d(json: Json, field: String, default: Double = 0.0): Double =
    json.get(field).map(_.asDouble).getOrElse(default)
  private def i(json: Json, field: String, default: Int = 0): Int =
    json.get(field).map(_.asInt).getOrElse(default)
  private def s(json: Json, field: String, default: String = ""): String =
    json.get(field).map(_.asString).getOrElse(default)

  implicit val rw: RW[InputMessage] = RW.from[InputMessage](
    r = {
      case MouseMove(x, y, buttons) =>
        obj("type" -> "mousemove", "x" -> x, "y" -> y, "buttons" -> buttons)
      case MouseDown(x, y, button, buttons, clickCount, modifiers) =>
        obj("type" -> "mousedown", "x" -> x, "y" -> y, "button" -> button, "buttons" -> buttons,
          "clickCount" -> clickCount, "modifiers" -> modifiers)
      case MouseUp(x, y, button, buttons, clickCount, modifiers) =>
        obj("type" -> "mouseup", "x" -> x, "y" -> y, "button" -> button, "buttons" -> buttons,
          "clickCount" -> clickCount, "modifiers" -> modifiers)
      case Wheel(x, y, deltaX, deltaY, modifiers) =>
        obj("type" -> "wheel", "x" -> x, "y" -> y, "deltaX" -> deltaX, "deltaY" -> deltaY, "modifiers" -> modifiers)
      case KeyDown(key, code, keyCode, text, modifiers) =>
        obj("type" -> "keydown", "key" -> key, "code" -> code, "keyCode" -> keyCode, "text" -> text, "modifiers" -> modifiers)
      case KeyUp(key, code, keyCode, text, modifiers) =>
        obj("type" -> "keyup", "key" -> key, "code" -> code, "keyCode" -> keyCode, "text" -> text, "modifiers" -> modifiers)
      case Char(text, key, keyCode) =>
        obj("type" -> "char", "text" -> text, "key" -> key, "keyCode" -> keyCode)
    },
    w = json => json("type").asString match {
      case "mousemove" => MouseMove(d(json, "x"), d(json, "y"), i(json, "buttons"))
      case "mousedown" => MouseDown(d(json, "x"), d(json, "y"), s(json, "button", "left"),
        i(json, "buttons", 1), i(json, "clickCount", 1), i(json, "modifiers"))
      case "mouseup" => MouseUp(d(json, "x"), d(json, "y"), s(json, "button", "left"),
        i(json, "buttons"), i(json, "clickCount", 1), i(json, "modifiers"))
      case "wheel" => Wheel(d(json, "x"), d(json, "y"), d(json, "deltaX"), d(json, "deltaY"), i(json, "modifiers"))
      case "keydown" => KeyDown(s(json, "key"), s(json, "code"), i(json, "keyCode"), s(json, "text"), i(json, "modifiers"))
      case "keyup" => KeyUp(s(json, "key"), s(json, "code"), i(json, "keyCode"), s(json, "text"), i(json, "modifiers"))
      case "char" => Char(s(json, "text"), s(json, "key"), i(json, "keyCode"))
      case other => throw new RuntimeException(s"Unknown InputMessage type: $other")
    },
    d = Definition(DefType.Json)
  )
}
