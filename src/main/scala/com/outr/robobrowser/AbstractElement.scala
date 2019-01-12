package com.outr.robobrowser

import org.openqa.selenium.By

trait AbstractElement {
  def by(by: By): List[WebElement]
  def byId(id: String): WebElement = by(By.id(id)).headOption.getOrElse(throw new RuntimeException(s"Not found by id: $id"))
  def byClass(className: String): List[WebElement] = by(By.className(className))
  def byName(name: String): List[WebElement] = by(By.name(name))
  def byTag(tagName: String): List[WebElement] = by(By.tagName(tagName))
}