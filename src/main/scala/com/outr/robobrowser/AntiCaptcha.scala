package com.outr.robobrowser

import java.io.File
import java.nio.file.{Files, StandardCopyOption}
import scala.concurrent.duration.DurationInt

object AntiCaptcha {
  def builder[T <: RoboBrowser](creator: Capabilities => T,
                                apiKey: String,
                                version: String = "0.60"): Capabilities => T = capabilities => {
    val browser = creator(capabilities)
    apply(browser, apiKey, version)
    browser
  }

  def apply(browser: RoboBrowser, apiKey: String, version: String): Unit = {
    browser.configuringOptions.attach { options =>
      scribe.info("Configuring options...")

      // Copy the CRX file to temporary files
      val crx = File.createTempFile("anticaptcha", ".crx")
      Files.copy(getClass.getClassLoader.getResourceAsStream(s"anticaptcha-plugin_v$version.crx"), crx.toPath, StandardCopyOption.REPLACE_EXISTING)
      crx.deleteOnExit()

      // Add the file as an extension
      scribe.info(s"Adding ${crx.getAbsolutePath} to extensions...")
      options.addExtensions(crx)
    }
    browser.initializing.attach { driver =>
      // Load configuration for
      scribe.info("Executing script...")
      driver.get("https://antcpt.com/blank.html")
      browser.execute(
        s"""
           |return window.postMessage({
           | 'receiver': 'antiCaptchaPlugin',
           | 'type': 'setOptions',
           | 'options': {
           |   'antiCaptchaApiKey': '$apiKey'
           | }
           |});
           |""".stripMargin)
    }
    browser.loaded.attach { url =>
      // TODO: support other captcha models
      val hasCaptcha = browser.firstBy(".g-recaptcha, .antigate_solver, #challenge-form").nonEmpty ||
        browser.by("iframe").exists(_.attribute("src").contains("hcaptcha.com"))
      if (hasCaptcha) {
        scribe.info("Captcha found! Waiting for solve...")
        browser.waitFor(180.seconds) {
          val waiting = browser.title.contains("Just a moment...") || browser.title.contains("Please Wait...")
          val solved = browser.firstBy(".antigate_solver.solved").nonEmpty
          val error = browser.firstBy(".antigate_solver.error").nonEmpty
          !waiting && (solved || error)
        }
        val solved = browser.firstBy(".antigate_solver").forall { solver =>
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
}