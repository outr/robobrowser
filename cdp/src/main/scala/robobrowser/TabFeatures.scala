package robobrowser

import fabric.io.JsonFormatter
import fabric._
import rapid.Task
import reactify.{Val, Var}
import robobrowser.comm.CommunicationManager
import robobrowser.event.ExecutionContext
import robobrowser.window.Frame

import scala.concurrent.duration.DurationInt

trait TabFeatures extends CommunicationManager {
  private[robobrowser] var targetId: String = _
  private[robobrowser] var sessionId: String = _

  protected val _loaded: Var[Boolean] = Var(true)
  protected val _executionContexts: Var[List[ExecutionContext]] = Var(Nil)

  def executionContexts: Val[List[ExecutionContext]] = _executionContexts
  val executionContext: Val[Option[ExecutionContext]] = Val(executionContexts.collectFirst {
    case ec if ec.auxData.frameId == targetId => ec
  })

  def navigate(url: String): Task[Frame] = {
    _loaded @= false
    send(
      method = "Page.navigate",
      params = obj(
        "url" -> url
      )
    ).map { response =>
      Frame(response.result("frameId").asString)
    }
  }

  def eval(expression: String): Task[Json] = send(
    method = "Runtime.evaluate",
    params = obj(
      "expression" -> expression
    )
  ).map(_.result)

  def callFunction(expression: String, objects: Json*): Task[Json] = {
    val params = objects.indices.map(i => s"obj${i + 1}").mkString(", ")
    send(
      method = "Runtime.callFunctionOn",
      params = obj(
        "functionDeclaration" -> s"function($params) { $expression; }",
        "arguments" -> objects.toList.map(arg => obj("value" -> arg)),
        "executionContextId" -> executionContext().get.id
      )
    ).map(_.result)
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
