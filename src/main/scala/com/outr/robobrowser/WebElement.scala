package com.outr.robobrowser

import org.openqa.selenium.By
import scala.jdk.CollectionConverters._

class WebElement(e: org.openqa.selenium.WebElement) extends AbstractElement {
  override def by(by: By): List[WebElement] = e.findElements(by).asScala.toList.map(new WebElement(_))
  override def by(cssSelector: String): List[WebElement] = e.findElements(By.cssSelector(cssSelector)).asScala.toList.map(new WebElement(_))

  def click(): Unit = e.click()
  def submit(): Unit = e.submit()

  def tagName: String = e.getTagName
  def text: String = e.getText
  def attribute(name: String): String = e.getAttribute(name)

  def sendInput(text: String): Unit = {
    e.click()
    e.sendKeys(text)
  }
}