package com.outr.robobrowser.browser.jsoup

import org.jsoup.nodes.Element
import org.openqa.selenium
import org.openqa.selenium.{Dimension, OutputType, Point, Rectangle}

import java.util

class JsoupWebElement(element: Element) extends selenium.WebElement {
  override def click(): Unit = ???

  override def submit(): Unit = ???

  override def sendKeys(keysToSend: CharSequence*): Unit = ???

  override def clear(): Unit = ???

  override def getTagName: String = element.tagName()

  override def getAttribute(name: String): String = element.attr(name)

  override def isSelected: Boolean = ???

  override def isEnabled: Boolean = ???

  override def getText: String = element.text()

  override def findElements(by: selenium.By): util.List[selenium.WebElement] = {
    val elements = element.select(JsoupWebDriver.by2CSS(by))
    val list = new util.ArrayList[selenium.WebElement](elements.size())
    (0 until elements.size()).foreach { index =>
      val element = elements.get(index)
      val e = new JsoupWebElement(element)
      list.add(e)
    }
    list
  }

  override def findElement(by: selenium.By): selenium.WebElement = {
    val elements = findElements(by)
    if (elements.size() > 0) {
      elements.get(0)
    } else {
      throw new NoSuchElementException(s"Element not found with selector: $by")
    }
  }

  override def isDisplayed: Boolean = ???

  override def getLocation: Point = ???

  override def getSize: Dimension = ???

  override def getRect: Rectangle = ???

  override def getCssValue(propertyName: String): String = ???

  override def getScreenshotAs[X](target: OutputType[X]): X = ???

  override def toString: String = element.toString
}