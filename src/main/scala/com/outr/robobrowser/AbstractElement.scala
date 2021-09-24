package com.outr.robobrowser

import org.openqa.selenium.By

import scala.concurrent.duration._

trait AbstractElement {
  protected def instance: RoboBrowser

  def by(by: By): List[WebElement]

  final def by(cssSelector: String): List[WebElement] = by(By.cssSelector(cssSelector))
  final def oneBy(by: By): WebElement = this.by(by) match {
    case element :: Nil => element
    case Nil => throw new RuntimeException(s"Nothing found by selector: ${by.toString}")
    case list => throw new RuntimeException(s"More than one found by selector: ${by.toString} ($list)")
  }
  final def oneBy(cssSelector: String): WebElement = oneBy(By.cssSelector(cssSelector))
  def firstBy(by: By): Option[WebElement] = this.by(by).headOption
  def firstBy(cssSelector: String): Option[WebElement] = this.by(cssSelector).headOption

  def clickWhenAvailable(cssSelector: String, timeout: FiniteDuration = 15.seconds): WebElement = {
    instance.waitFor(timeout) {
      firstBy(cssSelector).nonEmpty
    }
    val element = oneBy(cssSelector)
    element.click()
    element
  }

  def outerHTML: String
  def innerHTML: String
}