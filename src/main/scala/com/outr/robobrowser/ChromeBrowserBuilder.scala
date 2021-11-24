package com.outr.robobrowser

import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}

import java.io.{File, FileNotFoundException}

object ChromeBrowserBuilder {
  // TODO: Support auto-download of chromedriver from https://chromedriver.storage.googleapis.com/index.html
  // TODO: Detect installed version of Chrome using google-chrome --version
  private val searchPaths = List("/usr/bin/chromedriver", "/opt/homebrew/bin/chromedriver")
  private lazy val resolvedDriver: Option[File] = searchPaths
    .map(path => new File(path))
    .find(_.isFile)

  def create(capabilities: Capabilities): RoboBrowser = {
    new RoboBrowser(capabilities) {
      override type Driver = ChromeDriver

      override def sessionId: String = "Chrome"

      override protected def createWebDriver(options: ChromeOptions): Driver = {
        System.setProperty("webdriver.chrome.driver", capabilities.typed[String]("driverPath", findChromeDriver()))
        new ChromeDriver(options)
      }
    }
  }

  def findChromeDriver(): String = resolvedDriver
    .map(_.getCanonicalPath)
    .getOrElse(throw new FileNotFoundException(s"Unable to find ChromeDriver in ${searchPaths.mkString(", ")}"))
}