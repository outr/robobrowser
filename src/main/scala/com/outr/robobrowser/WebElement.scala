package com.outr.robobrowser

import org.jsoup.Jsoup
import org.openqa.selenium.By

import scala.jdk.CollectionConverters._
import scala.language.implicitConversions

class WebElement(e: org.openqa.selenium.WebElement) extends AbstractElement {
  override def by(by: By): List[WebElement] = e.findElements(by).asScala.toList.map(new WebElement(_))

  def click(): Unit = e.click()
  def submit(): Unit = e.submit()

  def tagName: String = e.getTagName
  def text: String = e.getText
  def attribute(name: String): String = e.getAttribute(name)
  def classes: Set[String] = attribute("class").split(' ').toSet

  def sendInput(text: String): Unit = {
    e.click()
    e.sendKeys(text)
  }

  def parsed(): ParsedElement = new ParsedElement(Jsoup.parseBodyFragment(outerHTML).body().child(0))

  override def outerHTML: String = e.getAttribute("outerHTML")

  override def innerHTML: String = e.getAttribute("innerHTML")

  override def toString: String = outerHTML
}