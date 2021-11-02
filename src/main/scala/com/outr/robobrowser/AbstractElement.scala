package com.outr.robobrowser

import org.openqa.selenium.{By, StaleElementReferenceException}

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

  /**
   * Special feature to work on zero, one, or many interacting as if it were a single element (similar to jQuery)
   */
  def on(by: By): WebElement = this.by(by) match {
    case e :: Nil => e
    case list => MultiElement(list)
  }

  /**
   * Special feature to work on zero, one, or many interacting as if it were a single element (similar to jQuery)
   */
  def on(cssSelector: String): WebElement = on(By.cssSelector(cssSelector))

  def clickWhenAvailable(cssSelector: String, timeout: FiniteDuration = 15.seconds): WebElement = {
    instance.waitFor(timeout) {
      firstBy(cssSelector).nonEmpty
    }
    avoidStaleReference {
      val element = on(cssSelector)
      element.click()
      element
    }
  }

  def avoidStaleReference[Return](f: => Return): Return = try {
    f
  } catch {
    case _: StaleElementReferenceException =>
      scribe.warn("Stale reference. Trying again.")
      avoidStaleReference(f)
  }

  def outerHTML: String
  def innerHTML: String
}