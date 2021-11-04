package com.outr.robobrowser

import org.jsoup.Jsoup
import org.openqa.selenium.By
import scala.jdk.CollectionConverters._

class SeleniumWebElement(e: org.openqa.selenium.WebElement, protected val instance: RoboBrowser) extends WebElement {
  override def by(by: By): List[WebElement] = e.findElements(by).asScala.toList.map(new SeleniumWebElement(_, instance))

  override def click(): WebElement = {
    e.click()
    this
  }

  override def submit(): WebElement = {
    e.submit()
    this
  }

  override def tagName: String = e.getTagName

  override def text: String = e.getText

  override def attribute(name: String): String = e.getAttribute(name)

  override def classes: Set[String] = attribute("class").split(' ').toSet

  override def sendInput(text: String): Unit = {
    e.click()
    e.sendKeys(text)
  }

  override def parsed(): ParsedElement = new ParsedElement(Jsoup.parseBodyFragment(outerHTML).body().child(0))

  override def outerHTML: String = e.getAttribute("outerHTML")

  override def innerHTML: String = e.getAttribute("innerHTML")

  override def toString: String = outerHTML
}
