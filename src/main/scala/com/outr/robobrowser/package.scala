package com.outr

import com.outr.robobrowser.appium.{AndroidCapabilities, IOSCapabilities}
import org.openqa.selenium.By

import scala.language.implicitConversions

package object robobrowser {
  implicit def string2BySelector(cssSelector: String): By = By.cssSelector(cssSelector)

  implicit def caps2Android[T <: RoboBrowser](builder: RoboBrowserBuilder[T]): AndroidCapabilities[T] = new AndroidCapabilities(builder)
  implicit def caps2IOS[T <: RoboBrowser](builder: RoboBrowserBuilder[T]): IOSCapabilities[T] = new IOSCapabilities[T](builder)
}