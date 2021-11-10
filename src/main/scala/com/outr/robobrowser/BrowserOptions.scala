//package com.outr.robobrowser
//
//import org.openqa.selenium.chrome.{ChromeOptions => SeleniumChromeOptions}
//
//trait BrowserOptions {
//  def headless: Boolean
//
//  def device: Device
//
//  def fakeMedia: Boolean
//
//  def toCapabilities: SeleniumChromeOptions = {
//    val options = new SeleniumChromeOptions
//
//    device.screenSize.foreach { s =>
//      options.addArguments(s"--window-size=${s.width},${s.height}")
//    }
//    device.identifier.foreach { id =>
//      options.setCapability("device", id)
//    }
//    device.osVersion.foreach { v =>
//      options.setCapability("os_version", v)
//    }
//    device.browserName.foreach { n =>
//      options.setCapability("browserName", n)
//    }
//    device.browser.foreach { b =>
//      options.setCapability("browser", b)
//    }
//    device.realMobile.foreach { b =>
//      options.setCapability("realMobile", b.toString)
//    }
//    options.addArguments(
//      "--ignore-certificate-errors",
//      "--no-sandbox",
//      "--disable-dev-shm-usage"
//    )
//
//    if (headless) {
//      options.addArguments(
//        "--headless",
//        "--disable-gpu"
//      )
//    }
//
//    device.userAgent.foreach { ua =>
//      options.addArguments(s"user-agent=$ua")
//    }
//    if (device.emulateMobile) {
//      val deviceMetrics = new java.util.HashMap[String, Any]
//      device.screenSize.foreach { s =>
//        deviceMetrics.put("width", s.width)
//        deviceMetrics.put("height", s.height)
//      }
//      val mobileEmulation = new java.util.HashMap[String, Any]
//      mobileEmulation.put("deviceMetrics", deviceMetrics)
//      mobileEmulation.put("userAgent", device.userAgent)
//      options.setExperimentalOption("mobileEmulation", mobileEmulation)
//    }
//
//    if (fakeMedia) {
//      options.addArguments("use-fake-device-for-media-stream")
//      options.addArguments("use-fake-ui-for-media-stream")
//    }
//
//    options
//  }
//}