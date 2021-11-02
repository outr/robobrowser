package com.outr.robobrowser

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