package robobrowser

import fabric.*
import fabric.rw.*
import rapid.Task
import spice.http.client.HttpClient
import spice.net.URL

import scala.concurrent.duration.*

/**
 * In-browser reCAPTCHA v2 image-challenge solving. Unlike [[AntiCaptchaSolver]] (which solves
 * the whole widget on the provider's own infrastructure and returns a token minted from THEIR
 * IP), this drives the challenge inside the real browser: it screenshots the challenge grid,
 * asks a coordinate solver which tiles to click, clicks them, and lets reCAPTCHA mint the
 * token in-page. The token is therefore minted from THIS browser's IP (i.e. through whatever
 * proxy the browser is using), which is what score-based deployments (reCAPTCHA Enterprise)
 * require — a datacenter/provider IP scores as a bot even with a correct answer.
 */
trait CoordinateCaptchaSolver {
  /** Classify a challenge image and return the click points (x, y) in image pixels. */
  def classifyCoordinates(imageBase64: String, comment: String): Task[List[(Double, Double)]]
}

/** Anti-Captcha `ImageToCoordinatesTask`: send the challenge screenshot (with its instruction
 *  visible), get back the points a human worker clicked. */
class AntiCaptchaCoordinateSolver(apiKey: String,
                                  baseUrl: String = "https://api.anti-captcha.com",
                                  pollInterval: FiniteDuration = 3.seconds,
                                  timeout: FiniteDuration = 120.seconds) extends CoordinateCaptchaSolver {
  private def post(path: String, body: Json): Task[Json] =
    HttpClient.url(URL.parse(s"$baseUrl$path")).json(body).call[Json]

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
        throw new RuntimeException(s"Anti-Captcha createTask(image) failed: $resp")
      poll(resp("taskId").asInt, System.currentTimeMillis())
    }
  }

  private def poll(taskId: Int, start: Long): Task[List[(Double, Double)]] =
    post("/getTaskResult", obj("clientKey" -> Str(apiKey), "taskId" -> NumInt(taskId.toLong))).flatMap { resp =>
      if (resp.get("errorId").map(_.asInt).getOrElse(0) != 0)
        throw new RuntimeException(s"Anti-Captcha getTaskResult(image) failed: $resp")
      resp.get("status").map(_.asString) match {
        case Some("ready") =>
          val coords = resp("solution").get("coordinates").map(_.asVector).getOrElse(Vector.empty).map { c =>
            val a = c.asVector
            (a.head.asBigDecimal.toDouble, a(1).asBigDecimal.toDouble)
          }.toList
          Task.pure(coords)
        case _ =>
          if (System.currentTimeMillis() - start > timeout.toMillis)
            throw new RuntimeException(s"Anti-Captcha image solve timed out (taskId=$taskId)")
          Task.sleep(pollInterval).flatMap(_ => poll(taskId, start))
      }
    }
}

object VisualCaptcha {
  // reCAPTCHA's Verify button sits at the bottom-right of the challenge frame.
  private val VerifyOffsetRight = 60.0
  private val VerifyOffsetBottom = 30.0

  extension (browser: RoboBrowser) {
    private def evalString(js: String): Task[String] =
      browser.eval(js).map(_("result").get("value").map(_.asString).getOrElse(""))

    /** (left, top, width, height) of the first visible iframe whose src contains `needle`. */
    private def visibleIframeRect(needle: String): Task[Option[(Double, Double, Double, Double)]] =
      evalString(
        s"""var fs=document.querySelectorAll('iframe');
           |for (var i=0;i<fs.length;i++){var f=fs[i];
           | if((f.src||'').indexOf('$needle')>=0){var r=f.getBoundingClientRect();
           |  if(r.width>10&&r.height>10) return [r.left,r.top,r.width,r.height].join(',');}}
           |return '';""".stripMargin).map { s =>
        if (s.isEmpty) None
        else { val a = s.split(",").map(_.toDouble); Some((a(0), a(1), a(2), a(3))) }
      }

    private def recaptchaToken: Task[Option[String]] =
      evalString("var e=document.querySelector('#g-recaptcha-response'); return (e&&e.value)?e.value:'';")
        .map(v => if (v.nonEmpty) Some(v) else None)

    private def trustedClick(x: Double, y: Double): Task[Unit] =
      browser.mouse.move(x, y).flatMap(_ => Task.sleep(120.millis))
        .flatMap(_ => browser.mouse.press(x, y)).flatMap(_ => Task.sleep(90.millis))
        .flatMap(_ => browser.mouse.release(x, y)).flatMap(_ => Task.sleep(250.millis))

    private def screenshotClip(x: Double, y: Double, w: Double, h: Double): Task[String] =
      browser.send("Page.captureScreenshot", obj(
        "format" -> Str("png"),
        "clip" -> obj(
          "x" -> NumDec(BigDecimal(x)), "y" -> NumDec(BigDecimal(y)),
          "width" -> NumDec(BigDecimal(w)), "height" -> NumDec(BigDecimal(h)),
          "scale" -> NumInt(1L)
        )
      )).map(_.result("data").asString)

    /**
     * Solve a reCAPTCHA v2 image challenge in-browser: click the checkbox, then repeatedly
     * screenshot the challenge, classify the tiles via `solver`, click them + Verify, until
     * the response token is populated. The token is minted in this browser (this browser's
     * IP). Returns true on success, false if unsolved within `maxRounds`.
     *
     * The browser MUST run with deviceScaleFactor 1 (e.g. BrowserConfig.forceDeviceScaleFactor
     * = Some(1)) so screenshot pixels map 1:1 to click coordinates.
     */
    def solveRecaptchaVisually(solver: CoordinateCaptchaSolver, maxRounds: Int = 8): Task[Boolean] = {
      val comment = "This is a reCAPTCHA. Follow the instruction shown at the top of the image and click the matching squares."

      def clickCheckbox: Task[Unit] = visibleIframeRect("anchor").flatMap {
        case Some((l, t, _, h)) => Task(scribe.info("VisualCaptcha: clicking checkbox")).flatMap(_ => trustedClick(l + 28.0, t + h / 2.0))
        case None => Task.unit
      }

      def rounds(n: Int): Task[Boolean] = recaptchaToken.flatMap {
        case Some(_) => Task.pure(true)
        case None if n >= maxRounds => Task(scribe.warn(s"VisualCaptcha: gave up after $maxRounds rounds")).map(_ => false)
        case None => visibleIframeRect("bframe").flatMap {
          case None => Task.sleep(1.second).flatMap(_ => rounds(n + 1)) // challenge not shown yet
          case Some((l, t, w, h)) =>
            screenshotClip(l, t, w, h).flatMap { img =>
              solver.classifyCoordinates(img, comment).flatMap { pts =>
                Task(scribe.info(s"VisualCaptcha round ${n + 1}: challenge ${w.toInt}x${h.toInt}, ${pts.size} tiles")).flatMap { _ =>
                  val clicks = pts.foldLeft(Task.unit) { case (acc, (cx, cy)) => acc.flatMap(_ => trustedClick(l + cx, t + cy)) }
                  clicks
                    .flatMap(_ => Task.sleep(600.millis))
                    .flatMap(_ => trustedClick(l + w - VerifyOffsetRight, t + h - VerifyOffsetBottom)) // Verify
                    .flatMap(_ => Task.sleep(2500.millis))
                    .flatMap(_ => rounds(n + 1))
                }
              }
            }
        }
      }

      clickCheckbox.flatMap(_ => Task.sleep(3.seconds)).flatMap(_ => rounds(0))
    }

    /** Convenience: solve visually using Anti-Captcha's image coordinate task. */
    def solveRecaptchaVisually(antiCaptchaApiKey: String): Task[Boolean] =
      solveRecaptchaVisually(new AntiCaptchaCoordinateSolver(antiCaptchaApiKey))
  }
}
