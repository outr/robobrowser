package com.outr.robobrowser.browser.htmlunit

case class HtmlUnitOptions(javaScript: Boolean) {
  def enableJavaScript: HtmlUnitOptions = copy(javaScript = true)

  def disableJavaScript: HtmlUnitOptions = copy(javaScript = false)

  def create(): HtmlUnit = new HtmlUnit(javaScript)
}