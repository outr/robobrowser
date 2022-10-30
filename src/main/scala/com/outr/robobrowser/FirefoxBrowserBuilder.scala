package com.outr.robobrowser

import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.{FirefoxDriver, FirefoxOptions, FirefoxProfile}

import java.io.{File, FileNotFoundException}

// TODO: Rewrite the `RoboBrowser.Firefox` concept to support FirefoxOptions
object FirefoxBrowserBuilder {
  private val searchPaths = List("/usr/bin/geckodriver", "/opt/homebrew/bin/geckodriver")
  private lazy val resolvedDriver: Option[File] = searchPaths
    .map(path => new File(path))
    .find(_.isFile)

  def create(capabilities: Capabilities): RoboBrowser = {
    new RoboBrowser(capabilities) {
      override type Driver = FirefoxDriver

      override def sessionId: String = "Chrome"

      override protected def createWebDriver(options: ChromeOptions): Driver = {
        System.setProperty("webdriver.firefox.driver", capabilities.typed[String]("driverPath", findFirefoxDriver()))
        val o = new FirefoxOptions(options)
        o.addPreference("media.eme.enabled", true)
        o.addPreference("media.gmp-manager.updateEnabled", true)
        //TODO: o.setProfile(new FirefoxProfile(profileDir))
        new FirefoxDriver(o)
      }
    }
  }

  def findFirefoxDriver(): String = resolvedDriver
    .map(_.getCanonicalPath)
    .getOrElse(throw new FileNotFoundException(s"Unable to find ChromeDriver in ${searchPaths.mkString(", ")}"))
}
