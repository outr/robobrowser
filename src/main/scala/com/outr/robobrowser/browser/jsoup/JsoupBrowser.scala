package com.outr.robobrowser.browser.jsoup

import com.outr.robobrowser.RoboBrowser

class JsoupBrowser(userAgent: String) extends RoboBrowser {
  override type Driver = JsoupWebDriver

  override def sessionId: String = "Jsoup"

  override protected lazy val _driver: Driver = new JsoupWebDriver(userAgent)
}

object Jsoup extends JsoupOptions("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.137 Safari/537.36")