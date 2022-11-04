package com.outr.robobrowser.browser.firefox

import org.openqa.selenium.firefox.{FirefoxOptions => SeleniumFirefoxOptions}

case class FirefoxOptions(options: SeleniumFirefoxOptions, driverPath: Option[String] = None) {
  def driverPath(path: String): FirefoxOptions = copy(driverPath = Some(path))

  protected def add(f: SeleniumFirefoxOptions => SeleniumFirefoxOptions): FirefoxOptions = {
    val o = new SeleniumFirefoxOptions
    copy(options = options.merge(f(o)))
  }

  protected def withArguments(arguments: String*): FirefoxOptions = add(_.addArguments(arguments: _*))

  def headless: FirefoxOptions = add(_.setHeadless(true))

  def windowSize(width: Int, height: Int): FirefoxOptions = withArguments(
    s"--width=$width",
    s"--height=$height"
  )

  def create(): Firefox = {
    System.setProperty("webdriver.firefox.driver", driverPath.getOrElse(Firefox.findGeckoDriver()))
    new Firefox(options)
  }
}
