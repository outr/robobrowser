package com.outr

import org.openqa.selenium.By

import scala.language.implicitConversions

package object robobrowser {
  implicit def string2BySelector(cssSelector: String): By = By.cssSelector(cssSelector)

  implicit def caps2Android(capabilities: Capabilities): AndroidCapabilities = new AndroidCapabilities(capabilities)

  implicit class iPhoneCapabilities(capabilities: Capabilities) {
    object `iPhone 12 Pro Max` {
      def `v14.0`: Capabilities = ???
    }
  }
}