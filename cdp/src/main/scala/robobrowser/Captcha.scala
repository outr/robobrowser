package robobrowser

import fabric.*
import fabric.rw.*
import fabric.io.JsonFormatter
import rapid.Task
import spice.http.client.HttpClient
import spice.net.URL

import scala.concurrent.duration.*

/**
 * Captcha widget kinds RoboBrowser can detect on a page and solve. The associated
 * values map each kind to its Anti-Captcha "proxyless" task type and the hidden
 * form field the widget's token is written into.
 */
enum CaptchaKind:
  case ReCaptchaV2, ReCaptchaV3, HCaptcha, Turnstile

  /** Anti-Captcha `createTask` `type` for this kind. */
  def antiCaptchaType: String = this match
    case CaptchaKind.ReCaptchaV2 => "RecaptchaV2TaskProxyless"
    case CaptchaKind.ReCaptchaV3 => "RecaptchaV3TaskProxyless"
    case CaptchaKind.HCaptcha    => "HCaptchaTaskProxyless"
    case CaptchaKind.Turnstile   => "TurnstileTaskProxyless"

  /** The hidden input/textarea `name` that holds the widget's token. */
  def responseField: String = this match
    case CaptchaKind.ReCaptchaV2 | CaptchaKind.ReCaptchaV3 => "g-recaptcha-response"
    case CaptchaKind.HCaptcha                              => "h-captcha-response"
    case CaptchaKind.Turnstile                             => "cf-turnstile-response"

object CaptchaKind:
  def fromDetected(s: String): Option[CaptchaKind] = s match
    case "turnstile"    => Some(Turnstile)
    case "hcaptcha"     => Some(HCaptcha)
    case "recaptcha_v2" => Some(ReCaptchaV2)
    case "recaptcha_v3" => Some(ReCaptchaV3)
    case _              => None

/** A captcha widget found on the current page. */
case class DetectedCaptcha(kind: CaptchaKind, siteKey: String, pageUrl: String, action: Option[String] = None)

/** A solved captcha token (plus the worker's User-Agent when the provider returns one). */
case class CaptchaSolution(token: String, userAgent: Option[String] = None)

/**
 * Pluggable captcha-solving backend. [[AntiCaptchaSolver]] is the default; implement
 * this trait to plug in a different provider (2Captcha, CapMonster, self-hosted, ...)
 * without touching RoboBrowser's detect/inject logic.
 */
trait CaptchaSolver:
  def solve(captcha: DetectedCaptcha): Task[CaptchaSolution]

/**
 * Anti-Captcha (anti-captcha.com) solver using the modern JSON API:
 * `createTask` -> poll `getTaskResult` until `ready`. Supports reCAPTCHA v2/v3,
 * hCaptcha and Cloudflare Turnstile via the corresponding `*Proxyless` task types.
 * Unlike the legacy in-page `imacros_inclusion` script, this reports explicit
 * errors/timeouts (no silent multi-minute hangs).
 */
class AntiCaptchaSolver(apiKey: String,
                        baseUrl: String = "https://api.anti-captcha.com",
                        pollInterval: FiniteDuration = 5.seconds,
                        timeout: FiniteDuration = 180.seconds,
                        softId: Option[Int] = None) extends CaptchaSolver:
  private def post(path: String, body: Json): Task[Json] =
    HttpClient.url(URL.parse(s"$baseUrl$path")).json(body).call[Json]

  private def errorText(resp: Json): String =
    s"${resp.get("errorCode").map(_.asString).getOrElse("")} ${resp.get("errorDescription").map(_.asString).getOrElse("")}".trim

  override def solve(captcha: DetectedCaptcha): Task[CaptchaSolution] = {
    val taskFields =
      List(
        "type"       -> Str(captcha.kind.antiCaptchaType),
        "websiteURL" -> Str(captcha.pageUrl),
        "websiteKey" -> Str(captcha.siteKey)
      ) ::: captcha.action.filter(_.nonEmpty).map(a => "action" -> Str(a)).toList
    val createBody = obj(
      (List("clientKey" -> Str(apiKey), "task" -> obj(taskFields*)) :::
        softId.map(id => "softId" -> NumInt(id.toLong)).toList)*
    )
    post("/createTask", createBody).flatMap { resp =>
      if (resp.get("errorId").map(_.asInt).getOrElse(0) != 0)
        throw new RuntimeException(s"Anti-Captcha createTask failed: ${errorText(resp)}")
      val taskId = resp("taskId").asInt
      poll(taskId, System.currentTimeMillis())
    }
  }

  private def poll(taskId: Int, start: Long): Task[CaptchaSolution] =
    post("/getTaskResult", obj("clientKey" -> Str(apiKey), "taskId" -> NumInt(taskId.toLong))).flatMap { resp =>
      if (resp.get("errorId").map(_.asInt).getOrElse(0) != 0)
        throw new RuntimeException(s"Anti-Captcha getTaskResult failed: ${errorText(resp)}")
      resp.get("status").map(_.asString) match
        case Some("ready") =>
          val solution = resp("solution")
          // Turnstile returns `token`; reCAPTCHA/hCaptcha return `gRecaptchaResponse`.
          val token = solution.get("token").orElse(solution.get("gRecaptchaResponse")).map(_.asString)
            .getOrElse(throw new RuntimeException(s"Anti-Captcha solution missing token: $solution"))
          Task.pure(CaptchaSolution(token, solution.get("userAgent").map(_.asString)))
        case _ =>
          if (System.currentTimeMillis() - start > timeout.toMillis)
            throw new RuntimeException(s"Anti-Captcha solve timed out after $timeout (taskId=$taskId)")
          Task.sleep(pollInterval).flatMap(_ => poll(taskId, start))
    }

object Captcha:
  val DefaultAutoPassWait: FiniteDuration = 15.seconds

  // Detect a captcha widget and report {kind, siteKey, action} (or null). Runs as the
  // body of an eval-wrapped function, so it ends in `return`.
  private val DetectJs: String =
    """
      |var el = document.querySelector('.cf-turnstile');
      |if (el) { return { kind: 'turnstile', siteKey: el.getAttribute('data-sitekey') || '', action: el.getAttribute('data-action') || '' }; }
      |el = document.querySelector('.h-captcha');
      |if (el) { return { kind: 'hcaptcha', siteKey: el.getAttribute('data-sitekey') || '', action: '' }; }
      |el = document.querySelector('.g-recaptcha');
      |if (el) { return { kind: 'recaptcha_v2', siteKey: el.getAttribute('data-sitekey') || '', action: '' }; }
      |var scripts = document.querySelectorAll('script[src]');
      |for (var i = 0; i < scripts.length; i++) {
      |  var m = (scripts[i].src || '').match(/recaptcha\/api\.js\?[^"']*[?&]render=([\w-]+)/);
      |  if (m && m[1] && m[1] !== 'explicit') { return { kind: 'recaptcha_v3', siteKey: m[1], action: '' }; }
      |}
      |return null;
    """.stripMargin

  private def injectJs(kind: CaptchaKind, token: String): String = {
    val t = JsonFormatter.Default(Str(token)) // safely-quoted/escaped JS string literal
    kind match
      case CaptchaKind.Turnstile =>
        s"""
           |var t = $t;
           |document.querySelectorAll('[name="cf-turnstile-response"], #cf-turnstile-response, [name="cf-chl-widget-response"]').forEach(function(e){ e.value = t; });
           |var w = document.querySelector('.cf-turnstile');
           |if (w) { var cb = w.getAttribute('data-callback'); if (cb && window[cb]) { try { window[cb](t); } catch (e) {} } }
         """.stripMargin
      case CaptchaKind.ReCaptchaV2 | CaptchaKind.ReCaptchaV3 =>
        s"""
           |var t = $t;
           |document.querySelectorAll('#g-recaptcha-response, [name="g-recaptcha-response"]').forEach(function(e){ e.value = t; e.innerHTML = t; });
           |try {
           |  if (window.___grecaptcha_cfg && ___grecaptcha_cfg.clients) {
           |    Object.keys(___grecaptcha_cfg.clients).forEach(function(k){
           |      var c = ___grecaptcha_cfg.clients[k];
           |      for (var p in c) { var o = c[p]; if (o && typeof o.callback === 'function') { try { o.callback(t); } catch (e) {} }
           |        for (var q in o) { var oo = o[q]; if (oo && typeof oo.callback === 'function') { try { oo.callback(t); } catch (e) {} } } }
           |    });
           |  }
           |} catch (e) {}
         """.stripMargin
      case CaptchaKind.HCaptcha =>
        s"""
           |var t = $t;
           |document.querySelectorAll('[name="h-captcha-response"], [name="g-recaptcha-response"], #h-captcha-response, #g-recaptcha-response').forEach(function(e){ e.value = t; e.innerHTML = t; });
           |var w = document.querySelector('.h-captcha');
           |if (w) { var cb = w.getAttribute('data-callback'); if (cb && window[cb]) { try { window[cb](t); } catch (e) {} } }
         """.stripMargin
  }

  extension (browser: RoboBrowser)
    /** Detect the captcha widget on the current page (kind + sitekey), if any. */
    def detectCaptcha: Task[Option[DetectedCaptcha]] = browser.eval(DetectJs).map { json =>
      json("result").get("value") match
        case Some(o: Obj) =>
          val kind = o.get("kind").map(_.asString).getOrElse("")
          val siteKey = o.get("siteKey").map(_.asString).getOrElse("")
          val action = o.get("action").map(_.asString).filter(_.nonEmpty)
          CaptchaKind.fromDetected(kind).filter(_ => siteKey.nonEmpty)
            .map(k => DetectedCaptcha(k, siteKey, browser.url(), action))
        case _ => None
    }

    /** The token currently populated in the widget's response field (empty => none). */
    def captchaToken(kind: CaptchaKind): Task[Option[String]] =
      browser.eval(s"""var e = document.querySelector('[name="${kind.responseField}"]') || document.getElementById('${kind.responseField}'); return (e && e.value) ? e.value : '';""")
        .map(_("result").get("value").map(_.asString).filter(_.nonEmpty))

    /** Poll up to `timeout` for the browser to self-issue a token (Turnstile /
     *  reCAPTCHA "managed" mode often passes real browsers for free). */
    def awaitCaptchaToken(kind: CaptchaKind, timeout: FiniteDuration): Task[Option[String]] =
      def loop(start: Long): Task[Option[String]] = captchaToken(kind).flatMap {
        case some @ Some(_) => Task.pure(some)
        case None =>
          if (System.currentTimeMillis() - start > timeout.toMillis) Task.pure(None)
          else Task.sleep(500.millis).flatMap(_ => loop(start))
      }
      loop(System.currentTimeMillis())

    /** Write a solved token into the widget's response field and fire its callback. */
    def injectCaptchaToken(kind: CaptchaKind, token: String): Task[Unit] =
      browser.eval(injectJs(kind, token)).unit

    /**
     * Detect the captcha on the current page and obtain a token: first wait briefly
     * for a browser-issued token (free, common with Turnstile/reCAPTCHA managed
     * mode); only if none appears, solve via `solver` and inject the result + fire
     * the widget callback. Returns true if a token was applied, false if no captcha
     * was present.
     */
    def solveCaptcha(solver: CaptchaSolver, autoPassWait: FiniteDuration): Task[Boolean] =
      detectCaptcha.flatMap {
        case None => Task.pure(false)
        case Some(captcha) =>
          awaitCaptchaToken(captcha.kind, autoPassWait).flatMap {
            case Some(_) => Task.pure(true) // browser already issued a valid token
            case None    => solver.solve(captcha).flatMap(s => injectCaptchaToken(captcha.kind, s.token)).map(_ => true)
          }
      }

    def solveCaptcha(solver: CaptchaSolver): Task[Boolean] = solveCaptcha(solver, DefaultAutoPassWait)

    /** Convenience: solve using Anti-Captcha with the given API key. */
    def solveCaptcha(antiCaptchaApiKey: String): Task[Boolean] =
      solveCaptcha(new AntiCaptchaSolver(antiCaptchaApiKey), DefaultAutoPassWait)
