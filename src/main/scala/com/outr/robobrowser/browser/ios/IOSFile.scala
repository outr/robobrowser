package com.outr.robobrowser.browser.ios

import com.outr.robobrowser.WebElement

case class IOSFile(element: WebElement) {
  lazy val name: String = element.attribute("name")

  def click(): Unit = element.click()
}