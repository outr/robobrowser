package com.outr.robobrowser

import scala.language.implicitConversions

trait WebElement extends AbstractElement {
  def click(): WebElement
  def submit(): WebElement

  def tagName: String
  def text: String
  def attribute(name: String): String
  def classes: Set[String]

  def sendInput(text: String): Unit

  def parsed(): ParsedElement
}