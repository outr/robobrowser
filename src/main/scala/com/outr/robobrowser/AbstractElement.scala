package com.outr.robobrowser

import com.outr.robobrowser.integration.AssertionFailed
import org.openqa.selenium.StaleElementReferenceException

import scala.concurrent.duration._

trait AbstractElement {
  protected def browser: RoboBrowser

  def by(by: By): List[WebElement]

  def children: List[WebElement]

  def capture(): Array[Byte]

  final def oneBy(by: By): WebElement = this.by(by) match {
    case element :: Nil => element
    case Nil => throw new RuntimeException(s"Nothing found by selector: ${by.toString}")
    case list => throw new RuntimeException(s"More than one found by selector: ${by.toString} ($list)")
  }
  def firstBy(by: By): Option[WebElement] = this.by(by).headOption

  /**
   * Special feature to work on zero, one, or many interacting as if it were a single element (similar to jQuery)
   */
  def on(by: By): WebElement = this.by(by) match {
    case e :: Nil => e
    case list => MultiElement(list)
  }

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

  def waitForFirst(timeout: FiniteDuration = 15.seconds, sleep: FiniteDuration = 500.millis)(bys: By*): WebElement = {
    avoidStaleReference {
      browser.waitForResult(timeout, sleep) {
        bys.flatMap(firstBy).headOption
      }
    }
  }

  def waitOn(by: By, timeout: FiniteDuration = 15.seconds, sleep: FiniteDuration = 500.millis): Option[WebElement] = {
    browser.waitFor(timeout, sleep) {
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