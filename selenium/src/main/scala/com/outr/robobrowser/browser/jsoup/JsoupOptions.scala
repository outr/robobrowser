package com.outr.robobrowser.browser.jsoup

case class JsoupOptions(userAgent: String) {
  def userAgent(userAgent: String): JsoupOptions = copy(userAgent = userAgent)

  def create(): JsoupBrowser = new JsoupBrowser(userAgent)
}