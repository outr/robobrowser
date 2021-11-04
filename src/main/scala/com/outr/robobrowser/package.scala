package com.outr

import org.openqa.selenium.By

import scala.language.implicitConversions

package object robobrowser {
  implicit def string2BySelector(cssSelector: String): By = By.cssSelector(cssSelector)
}