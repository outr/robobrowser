package robobrowser

import fabric.*
import fabric.rw.*
import rapid.Task
import spice.http.client.HttpClient
import spice.net.URL

import scala.concurrent.duration.*

/** Provider failures expose only a validated error code, never request or response payloads. */
private[robobrowser] final class AntiCaptchaImageError(val code:String)
  extends IllegalStateException("Anti-Captcha image classification failed"+(if(code.nonEmpty)s" ($code)"else""))

/** Sends a cropped in-browser challenge to Anti-Captcha's ImageToCoordinatesTask.
 * The provider supplies click coordinates; the website still decides whether to accept the challenge.
 */
class AntiCaptchaCoordinateSolver(apiKey: String,
                                  baseUrl: String = "https://api.anti-captcha.com",
                                  pollInterval: FiniteDuration = 3.seconds,
                                  timeout: FiniteDuration = 120.seconds) extends CoordinateCaptchaSolver {
  private def failure(response:Json):Throwable={
    val code=response.get("errorCode").collect{case s:Str if s.asString.matches("ERROR_[A-Z0-9_]{1,64}")=>s.asString}.getOrElse("")
    new AntiCaptchaImageError(code)
  }
  private def post(path: String, body: Json): Task[Json] =
    HttpClient.url(URL.parse(s"$baseUrl$path")).json(body).call[Json].handleError {
      case e:InterruptedException=>Task.error(e)
      case _=>Task.error(new AntiCaptchaImageError("TRANSPORT_FAILURE"))
    }

  override def classifyCoordinates(imageBase64: String, comment: String): Task[List[(Double, Double)]] = {
    val create = obj(
      "clientKey" -> Str(apiKey),
      "task" -> obj(
        "type" -> Str("ImageToCoordinatesTask"),
        "body" -> Str(imageBase64),
        "mode" -> Str("points"),
        "comment" -> Str(comment)
      )
    )
    post("/createTask", create).flatMap { resp =>
      if (resp.get("errorId").map(_.asInt).getOrElse(0) != 0)
        throw failure(resp)
      poll(resp("taskId").asLong, System.currentTimeMillis())
    }.handleError {
      case e: InterruptedException => Task.error(e)
      case e: AntiCaptchaImageError => Task.error(e)
      case _ => Task.error(new AntiCaptchaImageError("INVALID_RESPONSE"))
    }
  }

  private def poll(taskId: Long, start: Long): Task[List[(Double, Double)]] =
    post("/getTaskResult", obj("clientKey" -> Str(apiKey), "taskId" -> NumInt(taskId.toLong))).flatMap { resp =>
      if (resp.get("errorId").map(_.asInt).getOrElse(0) != 0)
        throw failure(resp)
      resp.get("status").map(_.asString) match {
        case Some("ready") =>
          val coords = resp("solution").get("coordinates").map(_.asVector).getOrElse(Vector.empty).map { c =>
            val a = c.asVector
            (a.head.asBigDecimal.toDouble, a(1).asBigDecimal.toDouble)
          }.toList
          Task.pure(coords)
        case _ =>
          if (System.currentTimeMillis() - start > timeout.toMillis)
            throw new AntiCaptchaImageError("TIMEOUT")
          Task.sleep(pollInterval).flatMap(_ => poll(taskId, start))
      }
    }
}

object VisualCaptcha {
  extension (browser: RoboBrowser) {
    /** Discovers nested and out-of-process frames; provider images exclude the surrounding page. */
    def solveRecaptchaVisually(solver: CoordinateCaptchaSolver, maxRounds: Int = 8): Task[Boolean] =
      new FrameCaptcha((method,params,session)=>browser.sendSession(method,params.asObj,session).map(_.result),Some(browser.sessionId)).solve(solver,maxRounds)
    def solveRecaptchaVisually(antiCaptchaApiKey: String): Task[Boolean] =
      solveRecaptchaVisually(new AntiCaptchaCoordinateSolver(antiCaptchaApiKey))
  }
  extension (tab: BrowserTab) {
    def solveRecaptchaVisually(solver: CoordinateCaptchaSolver, maxRounds: Int): Task[Boolean] =
      FrameCaptcha.forTab(tab).solve(solver,maxRounds)
    def solveRecaptchaVisually(antiCaptchaApiKey: String): Task[Boolean] =
      FrameCaptcha.forTab(tab).solve(new AntiCaptchaCoordinateSolver(antiCaptchaApiKey))
  }
}
