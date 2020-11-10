package com.outr.robobrowser

import org.openqa.selenium.By

trait AbstractElement {
  def by(by: By): List[WebElement]
  def by(cssSelector: String): List[WebElement]
  def byId(id: String): WebElement = by(By.id(id)).headOption.getOrElse(throw new RuntimeException(s"Not found by id: $id"))
  def byClass(className: String): List[WebElement] = by(By.className(className))
  def byName(name: String): List[WebElement] = by(By.name(name))
  def byTag(tagName: String): List[WebElement] = by(By.tagName(tagName))

  def firstBy(cssSelector: String): WebElement = by(cssSelector).headOption.getOrElse(throw new RuntimeException(s"Nothing found by CSS selector: $cssSelector"))
  def firstByClass(className: String): WebElement = byClass(className).headOption.getOrElse(throw new RuntimeException(s"Nothing found by class: $className"))
  def firstByName(name: String): WebElement = byName(name).headOption.getOrElse(throw new RuntimeException(s"Nothing found by name: $name"))

  def oneByName(name: String): WebElement = byName(name) match {
    case element :: Nil => element
    case Nil => throw new RuntimeException(s"Nothing found by name: $name")
    case list => throw new RuntimeException(s"More than one found by name: $name ($list)")
  }
}