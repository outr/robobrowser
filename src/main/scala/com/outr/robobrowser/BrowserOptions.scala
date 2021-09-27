package com.outr.robobrowser

import org.openqa.selenium.chrome.{ChromeOptions => SeleniumChromeOptions}

trait BrowserOptions {
  def headless: Boolean

  def device: Device

  def fakeMedia: Boolean

  def toCapabilities: SeleniumChromeOptions = {
    val options = new SeleniumChromeOptions

    options.addArguments(
      s"--window-size=${device.width},${device.height}",
      "--ignore-certificate-errors",
      "--no-sandbox",
      "--disable-dev-shm-usage"
    )

    if (headless) {
      options.addArguments(
        "--headless",
        "--disable-gpu"
      )
    }

    device.userAgent.foreach { ua =>
      options.addArguments(s"user-agent=$ua")
    }
    if (device.emulateMobile) {
      val deviceMetrics = new java.util.HashMap[String, Any]
      deviceMetrics.put("width", device.width)
      deviceMetrics.put("height", device.height)
      val mobileEmulation = new java.util.HashMap[String, Any]
      mobileEmulation.put("deviceMetrics", deviceMetrics)
      mobileEmulation.put("userAgent", device.userAgent)
      options.setExperimentalOption("mobileEmulation", mobileEmulation)
    }

    if (fakeMedia) {
      options.addArguments("use-fake-device-for-media-stream")
      options.addArguments("use-fake-ui-for-media-stream")
    }

    options
  }
}