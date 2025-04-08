package com.outr.robobrowser.browser.firefox

import com.outr.robobrowser.RoboBrowser
import com.outr.robobrowser.browser.BrowserOptions
import org.openqa.selenium.Capabilities
import org.openqa.selenium.firefox.{FirefoxProfile, FirefoxOptions => SeleniumFirefoxOptions}

import java.io.File

case class FirefoxOptions(options: SeleniumFirefoxOptions,
                          driverPath: Option[String] = None,
                          postInit: List[RoboBrowser => Unit]) extends BrowserOptions[FirefoxOptions] {
  override def withPostInit(f: RoboBrowser => Unit): FirefoxOptions = copy(postInit = postInit ::: List(f))

  override def capabilities: Capabilities = options
  override def merge(capabilities: Capabilities): FirefoxOptions = copy(options.merge(capabilities))

  def driverPath(path: String): FirefoxOptions = copy(driverPath = Some(path))

  protected def add(f: SeleniumFirefoxOptions => SeleniumFirefoxOptions): FirefoxOptions = {
    val o = new SeleniumFirefoxOptions
    copy(options = options.merge(f(o)))
  }

  protected def withArguments(arguments: String*): FirefoxOptions = add(_.addArguments(arguments: _*))

  protected def withPreferences(prefs: (String, Any)*): FirefoxOptions =
    prefs.foldLeft(this)((opts, pref) => opts.add(_.addPreference(pref._1, pref._2)))

  def headless: FirefoxOptions = withArguments("-headless")

  def windowSize(width: Int, height: Int): FirefoxOptions = withArguments(
    s"--width=$width",
    s"--height=$height"
  )

  def profile(profile: FirefoxProfile): FirefoxOptions = add(_.setProfile(profile))

  def userDataDir(path: String): FirefoxOptions = withArguments(
    s"--user-data-dir=$path",
    "--profile-directory=Default"
  )

  def enableDRM: FirefoxOptions = withPreferences(
    "media.eme.enabled" -> true,
    "media.gmp-manager.updateEnabled" -> true
  )

  def kiosk: FirefoxOptions = withArguments("--kiosk")

  def create(): Firefox = {
    System.setProperty("webdriver.firefox.driver", driverPath.getOrElse(Firefox.findGeckoDriver()))
    val b = new Firefox(options)
    postInit.foreach(f => f(b))
    b
  }
}