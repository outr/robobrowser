package com.outr.robobrowser.browser.firefox

import com.outr.robobrowser.RoboBrowser
import org.openqa.selenium.firefox.{FirefoxDriver, FirefoxOptions => SeleniumFirefoxOptions}

import java.io.{File, FileNotFoundException}
import java.nio.file.Path

class Firefox(options: SeleniumFirefoxOptions) extends RoboBrowser(options) {
  override type Driver = FirefoxDriver

  override def sessionId: String = "Firefox"

  def installExtension(path: Path): String = withDriver { driver =>
    driver.installExtension(path)
  }

  def uninstallExtension(extensionId: String): Unit = withDriver { driver =>
    driver.uninstallExtension(extensionId)
  }

  override protected def createDriver(): FirefoxDriver = new FirefoxDriver(options)
}

object Firefox extends FirefoxOptions(new SeleniumFirefoxOptions, None, Nil) {
  // TODO: Support auto-download of chromedriver from https://chromedriver.storage.googleapis.com/index.html
  // TODO: Detect installed version of Chrome using google-chrome --version
  private val searchPaths = List("/usr/bin/geckodriver", "/opt/homebrew/bin/geckodriver")
  private lazy val resolvedDriver: Option[File] = searchPaths
    .map(path => new File(path))
    .find(_.isFile)

  def findGeckoDriver(): String = resolvedDriver
    .map(_.getCanonicalPath)
    .getOrElse(throw new FileNotFoundException(s"Unable to find GeckoDriver in ${searchPaths.mkString(", ")}"))
}