package robobrowser

import robobrowser.Captcha.*
import robobrowser.select.Selector
import rapid.*

/**
 * Manual end-to-end check for the universal captcha solver against a real page.
 * Run: sbt "cdp/Test/runMain robobrowser.CaptchaManualTest <antiCaptchaApiKey> <pageUrl>"
 * Spends a small amount on the Anti-Captcha account if the page requires a solve.
 */
object CaptchaManualTest:
  def main(args: Array[String]): Unit =
    // Read via env vars to avoid shell/sbt mangling of the URL's `(`, `?`, `&`.
    val apiKey = sys.env.getOrElse("ANTICAPTCHA_KEY", args.headOption.getOrElse(""))
    val url = sys.env.getOrElse("TEST_URL", if (args.length > 1) args(1) else "")
    val headless = sys.env.getOrElse("HEADLESS", "true").toBoolean
    println(s"[test] url=$url headless=$headless")
    val task = RoboBrowser.withBrowser(RoboBrowserConfig(
      browser = Browser.Chrome,
      browserConfig = BrowserConfig(headless = headless, disableGPU = true, disableSoftwareRasterizer = true),
      tabSelector = TabSelector.AlwaysCreateNew
    )) { browser =>
      for
        _ <- browser.navigate(url)
        _ <- browser.waitForLoaded()
        pre <- browser.captchaToken(CaptchaKind.Turnstile)
        _ = println(s"[test] token already present on load (auto-pass?): ${pre.isDefined}")
        detected <- browser.detectCaptcha
        _ = println(s"[test] detectCaptcha: $detected")
        solved <- browser.solveCaptcha(apiKey)
        _ = println(s"[test] solveCaptcha returned: $solved")
        post <- browser.captchaToken(CaptchaKind.Turnstile)
        _ = println(s"[test] token after solve: ${post.map(t => t.take(24) + "...(len=" + t.length + ")")}")
        _ <- browser(Selector.Id("ctl00_ContentPlaceHolder1_PublicNoticeDetailsBody1_btnViewNotice")).click
        _ <- browser.waitForLoaded()
        content <- browser(Selector.Id("ctl00_ContentPlaceHolder1_PublicNoticeDetailsBody1_lblContentText")).innerText.map(_.mkString(" ").trim)
        _ = println(s"[test] CONTENT length=${content.length}; preview=${content.take(160)}")
      yield ()
    }
    task.sync()
    println("[test] done")
