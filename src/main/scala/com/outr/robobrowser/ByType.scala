package com.outr.robobrowser

import org.openqa.selenium.By

class ByType(f: String => By) {
  def create(value: String): By = f(value)
}

object ByType {
  case object Id extends ByType(By.id)
  case object `Link Text` extends ByType(By.linkText)
  case object `Partial Link Text` extends ByType(By.partialLinkText)
  case object Name extends ByType(By.name)
  case object `Tag Name` extends ByType(By.tagName)
  case object XPath extends ByType(By.xpath)
  case object `Class Name` extends ByType(By.className)
  case object `CSS Selector` extends ByType(By.cssSelector)

  lazy val all: List[ByType] = List(`CSS Selector`, Id, `Link Text`, `Partial Link Text`, Name, `Tag Name`, XPath, `Class Name`)
}