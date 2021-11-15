package com.outr.robobrowser

import com.outr.robobrowser.integration.AssertionFailed
import org.openqa.selenium.{By, StaleElementReferenceException}

import scala.concurrent.duration._

trait AbstractElement {
  protected def instance: RoboBrowser

  def by(by: By): List[WebElement]

  def capture(): Array[Byte]

  final def oneBy(by: By): WebElement = this.by(by) match {
    case element :: Nil => element
    case Nil => throw new RuntimeException(s"Nothing found by selector: ${by.toString}")
    case list => throw new RuntimeException(s"More than one found by selector: ${by.toString} ($list)")
  }
  final def oneBy(cssSelector: String): WebElement = oneBy(By.cssSelector(cssSelector))
  def firstBy(by: By): Option[WebElement] = this.by(by).headOption

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

  def clickWhenAvailable(by: By,
                         timeout: FiniteDuration = 15.seconds,
                         sleep: FiniteDuration = 500.millis): WebElement = {
    avoidStaleReference {
      waitOn(by, timeout, sleep) match {
        case Some(e) =>
          e.click()
          e
        case None => throw AssertionFailed(s"Not found by selector: $by")
      }
    }
  }

  def waitOn(by: By, timeout: FiniteDuration = 15.seconds, sleep: FiniteDuration = 500.millis): Option[WebElement] = {
    instance.waitFor(timeout, sleep) {
      firstBy(by).nonEmpty
    }
    firstBy(by)
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