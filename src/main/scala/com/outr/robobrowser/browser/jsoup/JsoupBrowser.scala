package com.outr.robobrowser.browser.jsoup

import com.outr.robobrowser.RoboBrowser
import org.openqa.selenium.ImmutableCapabilities

class JsoupBrowser(userAgent: String) extends RoboBrowser(new ImmutableCapabilities) {
  override type Driver = JsoupWebDriver

  override def sessionId: String = "Jsoup"

  override protected def createDriver(): Driver = new JsoupWebDriver(userAgent)
}

object JsoupBrowser extends JsoupOptions("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.137 Safari/537.36")