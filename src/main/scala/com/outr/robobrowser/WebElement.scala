package com.outr.robobrowser

import org.jsoup.Jsoup
import org.openqa.selenium.By

import scala.jdk.CollectionConverters._
import scala.language.implicitConversions

trait WebElement extends AbstractElement {
  def click(): Unit
  def submit(): Unit

  def tagName: String
  def text: String
  def attribute(name: String): String
  def classes: Set[String]

  def sendInput(text: String): Unit

  def parsed(): ParsedElement
}

class SeleniumWebElement(e: org.openqa.selenium.WebElement, protected val instance: RoboBrowser) extends WebElement {
  override def by(by: By): List[WebElement] = e.findElements(by).asScala.toList.map(new SeleniumWebElement(_, instance))

  override def click(): Unit = e.click()
  override def submit(): Unit = e.submit()

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

case class MultiElement(elements: List[WebElement]) extends WebElement {
  override def click(): Unit = elements.foreach(_.click())

  override def submit(): Unit = elements.foreach(_.submit())

  override def tagName: String = ""

  override def text: String = ""

  override def attribute(name: String): String = elements.map(_.attribute(name)).mkString(",")

  override def classes: Set[String] = elements.flatMap(_.classes).toSet

  override def sendInput(text: String): Unit = elements.foreach(_.sendInput(text))

  override def parsed(): ParsedElement = ???

  override protected def instance: RoboBrowser = ???

  override def by(by: By): List[WebElement] = elements.flatMap(_.by(by)).distinct

  override def outerHTML: String = elements.map(_.outerHTML).mkString(", ")

  override def innerHTML: String = elements.map(_.innerHTML).mkString(", ")
}