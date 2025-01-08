package robobrowser

import fabric.{Json, Null, obj}
import rapid.Task
import robobrowser.comm.CommunicationManager
import robobrowser.window.Frame

import scala.concurrent.duration.DurationInt

trait TabFeatures extends CommunicationManager {
  private[robobrowser] var targetId: String = _
  private[robobrowser] var sessionId: String = _

  def navigate(url: String): Task[Frame] = send(
    method = "Page.navigate",
    params = obj(
      "url" -> url
    )
  ).map { response =>
    Frame(response.result("frameId").asString)
  }

  def eval(expression: String): Task[Json] = send(
    method = "Runtime.evaluate",
    params = obj(
      "expression" -> expression
    )
  ).map { response =>
    response.result.get("value").getOrElse(Null)
  }

  def bringToFront(): Task[Unit] = send(
    method = "Page.bringToFront"
  ).unit

  def keyType(keyId: Int): Task[Unit] = for {
    _ <- keyDown(keyId)
    _ <- Task.sleep(500.millis)
    _ <- keyUp(keyId)
  } yield ()

  def keyDown(keyId: Int): Task[Unit] = send(
    method = "Input.dispatchKeyEvent",
    params = obj(
      "type" -> "keyDown",
      "windowsVirtualKeyCode" -> keyId,
      "nativeVirtualKeyCode" -> keyId
    )
  ).unit

  def keyUp(keyId: Int): Task[Unit] = send(
    method = "Input.dispatchKeyEvent",
    params = obj(
      "type" -> "keyUp",
      "windowsVirtualKeyCode" -> keyId,
      "nativeVirtualKeyCode" -> keyId
    )
  ).unit
}
