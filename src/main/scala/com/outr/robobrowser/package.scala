package com.outr

import com.outr.robobrowser.appium.{AndroidCapabilities, Appium, IOSCapabilities}
import org.openqa.selenium.By

import scala.language.implicitConversions

package object robobrowser {
  type MobileBrowser = RoboBrowser with Appium

  implicit def string2BySelector(cssSelector: String): By = By.cssSelector(cssSelector)

  implicit def caps2Android[T <: RoboBrowser](builder: RoboBrowserBuilder[T]): AndroidCapabilities[T] = new AndroidCapabilities(builder)

  implicit def caps2IOS[T <: RoboBrowser](builder: RoboBrowserBuilder[T]): IOSCapabilities[T] = new IOSCapabilities[T](builder)

  implicit class BuilderConversions[T <: RoboBrowser](builder: RoboBrowserBuilder[T]) {
    def antiCaptcha(apiKey: String, version: String = "0.60"): RoboBrowserBuilder[T] =
      builder.withCreator(AntiCaptcha.builder(builder.creator, apiKey, version))

    def browserStack(options: BrowserStackOptions): RoboBrowserBuilder[T] = BrowserStack(builder, options)
  }

  implicit def browser2BrowserStack(browser: RoboBrowser): BrowserStack = new BrowserStack(browser)
}