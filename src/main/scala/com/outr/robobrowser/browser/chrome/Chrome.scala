package com.outr.robobrowser.browser.chrome

import com.outr.robobrowser.RoboBrowser
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions => SeleniumChromeOptions}

import java.io.{File, FileNotFoundException}

class Chrome(options: SeleniumChromeOptions) extends RoboBrowser {
  override type Driver = ChromeDriver

  override def sessionId: String = "Chrome"

  override protected lazy val _driver: ChromeDriver = new ChromeDriver(options)
}

object Chrome extends ChromeOptions(new SeleniumChromeOptions) {
  // TODO: Support auto-download of chromedriver from https://chromedriver.storage.googleapis.com/index.html
  // TODO: Detect installed version of Chrome using google-chrome --version
  private val searchPaths = List("/usr/bin/chromedriver", "/opt/homebrew/bin/chromedriver")
  private lazy val resolvedDriver: Option[File] = searchPaths
    .map(path => new File(path))
    .find(_.isFile)

  def findChromeDriver(): String = resolvedDriver
    .map(_.getCanonicalPath)
    .getOrElse(throw new FileNotFoundException(s"Unable to find ChromeDriver in ${searchPaths.mkString(", ")}"))
}