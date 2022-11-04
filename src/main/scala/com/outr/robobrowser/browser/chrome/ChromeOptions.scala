package com.outr.robobrowser.browser.chrome

import org.openqa.selenium.chrome.{ChromeOptions => SeleniumChromeOptions}

case class ChromeOptions(options: SeleniumChromeOptions, driverPath: Option[String] = None) {
  def driverPath(path: String): ChromeOptions = copy(driverPath = Some(path))

  protected def add(f: SeleniumChromeOptions => SeleniumChromeOptions): ChromeOptions = {
    val o = new SeleniumChromeOptions
    copy(options = options.merge(f(o)))
  }

  protected def withArgument(argument: String): ChromeOptions = add(_.addArguments(argument))

  def headless: ChromeOptions = withArgument("--headless")

  def windowSize(width: Int, height: Int): ChromeOptions = withArgument(s"--window-size=$width,$height")

  def create(): Chrome = {
    System.setProperty("webdriver.chrome.driver", driverPath.getOrElse(Chrome.findChromeDriver()))
    new Chrome(options)
  }
}
