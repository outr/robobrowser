package com.outr.robobrowser

import org.openqa.selenium.By

case class MultiElement(elements: List[WebElement]) extends WebElement {
  override def click(): WebElement = {
    elements.foreach(_.click())
    this
  }

  override def submit(): WebElement = {
    elements.foreach(_.submit())
    this
  }

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