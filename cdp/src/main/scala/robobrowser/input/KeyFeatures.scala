package robobrowser.input

import fabric._
import fabric.dsl._
import rapid._
import robobrowser.RoboBrowser

import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class KeyFeatures(browser: RoboBrowser) {
  def down(key: Key): Task[Unit] = browser.send(
    method = "Input.dispatchKeyEvent",
    params = obj(
      "type" -> "keyDown",
      "key" -> key.char.map(c => str(c.toString)).getOrElse(Null),
      "windowsVirtualKeyCode" -> key.id,
      "nativeVirtualKeyCode" -> key.id
    )
  ).unit

  def char(key: Key): Task[Unit] = {
    val char: Json = key.char.map(c => str(c.toString)).getOrElse(Null)
    browser.send(
      method = "Input.dispatchKeyEvent",
      params = obj(
        "type" -> "char",
        "key" -> char,
        "text" -> char,
        "windowsVirtualKeyCode" -> key.id,
        "nativeVirtualKeyCode" -> key.id
      )
    ).unit
  }

  def up(key: Key): Task[Unit] = browser.send(
    method = "Input.dispatchKeyEvent",
    params = obj(
      "type" -> "keyUp",
      "key" -> key.char.map(c => str(c.toString)).getOrElse(Null),
      "windowsVirtualKeyCode" -> key.id,
      "nativeVirtualKeyCode" -> key.id
    )
  ).unit

  def `type`(key: Key, delay: FiniteDuration = 0.millis): Task[Unit] = for {
    _ <- down(key)
    _ <- Task.sleep(delay)
    _ <- char(key).when(key.char.nonEmpty)
    _ <- Task.sleep(delay)
    _ <- up(key)
  } yield ()

  def send(keys: List[Key], delay: FiniteDuration = 0.millis): Task[Unit] = keys.map(`type`(_, delay)).tasks.unit

  def around[Return](key: Key)(f: => Task[Return]): Task[Return] = for {
    _ <- down(key)
    r <- f
    _ <- up(key)
  } yield r

  /** Raw key-event dispatch — passes DOM key fields straight to CDP
    * `Input.dispatchKeyEvent`, for forwarding live keystrokes from a UI (where
    * the producing app already has the DOM `key`/`code`/`text`). `eventType` is
    * `keyDown` | `keyUp` | `rawKeyDown` | `char`; `modifiers` is the CDP bitmask
    * (alt=1, ctrl=2, meta=4, shift=8). For a printable character a `char` event
    * with `text` set inserts the text; for control keys (Enter, Backspace,
    * arrows, …) use `keyDown`/`keyUp` with `windowsVirtualKeyCode`. */
  def dispatch(eventType: String,
               key: String = "",
               code: String = "",
               text: String = "",
               windowsVirtualKeyCode: Int = 0,
               modifiers: Int = 0): Task[Unit] = browser.send(
    method = "Input.dispatchKeyEvent",
    params = obj(
      "type" -> eventType,
      "key" -> key,
      "code" -> code,
      "text" -> text,
      "windowsVirtualKeyCode" -> windowsVirtualKeyCode,
      "nativeVirtualKeyCode" -> windowsVirtualKeyCode,
      "modifiers" -> modifiers
    )
  ).unit
}