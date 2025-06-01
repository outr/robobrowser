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
      "key" -> key.char.map(c => str(c.toString)).orElse(Null),
      "windowsVirtualKeyCode" -> key.id,
      "nativeVirtualKeyCode" -> key.id
    )
  ).unit

  def char(key: Key): Task[Unit] = {
    val char: Json = key.char.map(c => str(c.toString)).orElse(Null)
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
      "key" -> key.char.map(c => str(c.toString)).orElse(Null),
      "windowsVirtualKeyCode" -> key.id,
      "nativeVirtualKeyCode" -> key.id
    )
  ).unit

  def `type`(key: Key, delay: FiniteDuration = 0.millis): Task[Unit] = for {
    _ <- down(key)
    _ <- Task.sleep(delay)
    _ <- char(key).when(key.char.notEmpty)
    _ <- Task.sleep(delay)
    _ <- up(key)
  } yield ()

  def send(keys: List[Key], delay: FiniteDuration = 0.millis): Task[Unit] = keys.map(`type`(_, delay)).tasks.unit

  def around[Return](key: Key)(f: => Task[Return]): Task[Return] = for {
    _ <- down(key)
    r <- f
    _ <- up(key)
  } yield r
}