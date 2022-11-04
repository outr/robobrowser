package com.outr.robobrowser.browser.firefox

import org.openqa.selenium.firefox.{FirefoxProfile, FirefoxOptions => SeleniumFirefoxOptions}

import java.io.File

case class FirefoxOptions(options: SeleniumFirefoxOptions, driverPath: Option[String] = None) {
  def driverPath(path: String): FirefoxOptions = copy(driverPath = Some(path))

  protected def add(f: SeleniumFirefoxOptions => SeleniumFirefoxOptions): FirefoxOptions = {
    val o = new SeleniumFirefoxOptions
    copy(options = options.merge(f(o)))
  }

  protected def withArguments(arguments: String*): FirefoxOptions = add(_.addArguments(arguments: _*))

  protected def withPreferences(prefs: (String, Any)*): FirefoxOptions =
    prefs.foldLeft(this)((opts, pref) => opts.add(_.addPreference(pref._1, pref._2)))

  def headless: FirefoxOptions = add(_.setHeadless(true))

  def windowSize(width: Int, height: Int): FirefoxOptions = withArguments(
    s"--width=$width",
    s"--height=$height"
  )

  def profileDir(dir: File): FirefoxOptions = add(_.setProfile(new FirefoxProfile(dir)))

  def enableDRM: FirefoxOptions = withPreferences(
    "media.eme.enabled" -> true,
    "media.gmp-manager.updateEnabled" -> true
  )

  def create(): Firefox = {
    System.setProperty("webdriver.firefox.driver", driverPath.getOrElse(Firefox.findGeckoDriver()))
    new Firefox(options)
  }
}