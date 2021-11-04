package com.outr.robobrowser

import io.youi.net.URL
import org.openqa.selenium.chrome.ChromeOptions

import java.io.File
import java.nio.file.{Files, StandardCopyOption}
import scala.concurrent.duration.DurationInt

trait AntiCaptcha extends RoboBrowser {
  protected def antiCaptchaApiKey: String
  protected def antiCaptchaVersion: String = "0.60"

  override protected def configureOptions(options: ChromeOptions): Unit = {
    super.configureOptions(options)

    scribe.info("Configuring options...")

    // Copy the CRX file to temporary files
    val crx = File.createTempFile("anticaptcha", ".crx")
    Files.copy(getClass.getClassLoader.getResourceAsStream(s"anticaptcha-plugin_v$antiCaptchaVersion.crx"), crx.toPath, StandardCopyOption.REPLACE_EXISTING)
    crx.deleteOnExit()

    // Add the file as an extension
    scribe.info(s"Adding ${crx.getAbsolutePath} to extensions...")
    options.addExtensions(crx)
  }

  override protected def initialize(): Unit = {
    super.initialize()

    // Load configuration for
    scribe.info("Executing script...")
    driver.get("https://antcpt.com/blank.html")
    execute(
      s"""
        |return window.postMessage({
        | 'receiver': 'antiCaptchaPlugin',
        | 'type': 'setOptions',
        | 'options': {
        |   'antiCaptchaApiKey': '$antiCaptchaApiKey'
        | }
        |});
        |""".stripMargin)
    Thread.sleep(3000)
  }

  override def load(url: URL): Unit = {
    super.load(url)

    // TODO: support other captcha models
    val hasCaptcha = firstBy(".g-recaptcha, .antigate_solver, #challenge-form").nonEmpty ||
      by("iframe").exists(_.attribute("src").contains("hcaptcha.com"))
    if (hasCaptcha) {
      scribe.info("Captcha found! Waiting for solve...")
      waitFor(180.seconds) {
        val waiting = title.contains("Just a moment...") || title.contains("Please Wait...")
        val solved = firstBy(".antigate_solver.solved").nonEmpty
        val error = firstBy(".antigate_solver.error").nonEmpty
        !waiting && (solved || error)
      }
      val solved = firstBy(".antigate_solver").forall { solver =>
        val classes = solver.classes
        classes.contains("solved")
      }
      if (solved) {
        scribe.info("Solved successfully!")
      } else {
        scribe.error(s"Not solved!")
      }
    }
  }
}