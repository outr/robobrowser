package robobrowser

import fabric.io.JsonFormatter
import fabric._
import fabric.dsl._
import rapid.{Task, logger}
import reactify._
import robobrowser.comm.CommunicationManager
import robobrowser.event.ExecutionContext
import robobrowser.input.KeyFeatures
import robobrowser.window.Frame

import scala.concurrent.duration.DurationInt

trait TabFeatures extends CommunicationManager {
  protected val _loaded: Var[Boolean] = Var(true)
  protected val _executionContexts: Var[List[ExecutionContext]] = Var(Nil)

  def executionContexts: Val[List[ExecutionContext]] = _executionContexts
  val executionContext: Val[Option[ExecutionContext]] = Val(executionContexts.collectFirst {
    case ec if ec.auxData.frameId == targetId => ec
  })

  def navigate(url: String, attempts: Int = 0): Task[Frame] = {
    _loaded @= false
    send(
      method = "Page.navigate",
      params = obj(
        "url" -> url
      )
    ).flatMap { response =>
      response.result.get("errorText").map(_.asString) match {
        case Some(errorText) if attempts < RoboBrowser.NavigateRetries => logger.warn(s"Failed to navigate to $url (attempt: $attempts): $errorText. Trying again in five seconds...")
          .next(Task.sleep(5.seconds))
          .next(navigate(url, attempts + 1))
        case Some(errorText) => throw new RuntimeException(errorText)
        case None => Task.pure(Frame(response.result("frameId").asString))
      }
    }
  }

  def eval(expression: String): Task[Json] = send(
    method = "Runtime.evaluate",
    params = obj(
      "expression" -> s"(function() { $expression })()",
      "returnByValue" -> true
    )
  ).map { response =>
    response.error match {
      case Some(error) => throw new RuntimeException(s"Evaluation error for $expression: ${error.message} (${error.code})")
      case None => response.result
    }
  }

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
}
