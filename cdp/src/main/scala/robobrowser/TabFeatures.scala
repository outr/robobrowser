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
import spice.net.URL

import scala.concurrent.duration.DurationInt
import scala.io.Source

trait TabFeatures extends CommunicationManager {
  protected val _loaded: Var[Boolean] = Var(true)
  protected val _executionContexts: Var[List[ExecutionContext]] = Var(Nil)

  def executionContexts: Val[List[ExecutionContext]] = _executionContexts
  val executionContext: Val[Option[ExecutionContext]] = Val(executionContexts.collectFirst {
    case ec if ec.auxData.frameId == targetId => ec
  })

  def navigate(url: String, attempts: Int = 0): Task[Option[Frame]] = {
    _loaded @= false
    send(
      method = "Page.navigate",
      params = obj(
        "url" -> url
      )
    ).flatMap { response =>
      response.result.get("errorText").map(_.asString) match {
        case Some(errorText) if errorText == "net::ERR_ABORTED" =>
          val parsed = URL.parse(url)
          if (DownloadState.wasRecentlyTriggered(parsed, includeHost = true)) {
            throw DownloadState.DownloadTriggeredException(parsed)
          } else if (attempts < RoboBrowser.NavigateRetries) {
            logger.warn(s"Failed to navigate to $url (attempt: $attempts): $errorText. Trying again in five seconds...")
              .next(Task.sleep(5.seconds))
              .next(navigate(url, attempts + 1))
          } else {
            throw new RuntimeException(errorText)
          }
        case Some(errorText) if errorText == "net::ERR_HTTP_RESPONSE_CODE_FAILURE" => logger
          .warn(s"Response code failure ($url)! ${response.error}")
          .map(_ => None)
        case Some(errorText) if errorText == "net::ERR_NAME_NOT_RESOLVED" => logger
          .warn(s"Name not resolved ($url)! ${response.error}")
          .map(_ => None)
        case Some(errorText) if attempts < RoboBrowser.NavigateRetries => logger.warn(s"Failed to navigate to $url (attempt: $attempts): $errorText. Trying again in five seconds...")
          .next(Task.sleep(5.seconds))
          .next(navigate(url, attempts + 1))
        case Some(errorText) => throw new RuntimeException(s"$errorText for $url")
        case None => Task.pure(Some(Frame(response.result("frameId").asString)))
      }
    }
  }

  def eval(expression: String, awaitPromise: Boolean = false): Task[Json] = send(
    method = "Runtime.evaluate",
    params = obj(
      "expression" -> s"(function() { $expression })()",
      "returnByValue" -> true,
      "awaitPromise" -> awaitPromise
    )
  ).map { response =>
    response.error match {
      case Some(error) => throw new RuntimeException(s"Evaluation error for $expression: ${error.message} (${error.code})")
      case None => response.result
    }
  }

  def executeScript(resourceName: String): Task[Json] = Task.defer {
    val url = getClass.getClassLoader.getResource(resourceName)
    val source = Source.fromURL(url)
    val js = try {
      source.mkString
    } finally {
      source.close()
    }
    eval(js)
  }

  def loadLibrary(url: String): Task[Json] = Task.defer {
    val urlEncoded = encodeForJsString(url)
    val js =
      s"""
         |return (function() {
         |  const url = $urlEncoded;
         |  console.log("[CDP] loading script:", url);
         |
         |  // Avoid double injection
         |  const existing = document.querySelector(
         |    'script[data-cdp-injected="' + url + '"]'
         |  );
         |  if (existing) {
         |    console.log("[CDP] already loaded:", url);
         |    return "already-loaded";
         |  }
         |
         |  return new Promise((resolve, reject) => {
         |    const s = document.createElement('script');
         |    s.src = url;
         |    s.async = true;
         |    s.dataset.cdpInjected = url;
         |    s.onload = () => {
         |      console.log("[CDP] loaded:", s.src);
         |      resolve("ok");
         |    };
         |    s.onerror = (e) => {
         |      console.error("[CDP] failed:", s.src, e);
         |      reject(new Error("Failed to load " + s.src));
         |    };
         |    document.head.appendChild(s);
         |  });
         |})();
         |""".stripMargin

    eval(js, awaitPromise = true)
  }

  private def encodeForJsString(s: String): String =
    "\"" + s
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r") + "\""


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
