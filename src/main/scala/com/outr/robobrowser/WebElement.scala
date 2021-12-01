package com.outr.robobrowser

import scala.language.implicitConversions

trait WebElement extends AbstractElement {
  def click(): WebElement
  def submit(): WebElement

  def tagName: String
  def text: String
  def attribute(name: String): String
  def attribute(name: String, value: Any): WebElement
  def style(name: String): Any
  def style(name: String, value: Any): WebElement
  def classes: Set[String]

  def isDisplayed: Boolean
  def isEnabled: Boolean
  def isSelected: Boolean
  def isClickable: Boolean = isDisplayed && isEnabled

  def sendInput(text: String): Unit

  def parsed(): ParsedElement
}