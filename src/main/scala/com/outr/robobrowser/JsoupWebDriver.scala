package com.outr.robobrowser

import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import org.openqa.selenium
import org.openqa.selenium.{Dimension, OutputType, Point, Rectangle, WebDriver}
import org.openqa.selenium.chrome.ChromeOptions
import spice.net._

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

class JsoupWebDriver(userAgent: String = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.137 Safari/537.36") extends WebDriver {
  private var url: String = "about:blank"
  private var doc: Document = JsoupWebDriver.emptyDoc

  override def get(url: String): Unit = {
    this.url = url
    doc = Jsoup.connect(url).userAgent(userAgent).get()
  }

  override def getCurrentUrl: String = url

  override def getTitle: String = doc.title()

  override def findElements(by: selenium.By): util.List[selenium.WebElement] = {
    val elements = doc.select(JsoupWebDriver.by2CSS(by))
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

  override def getPageSource: String = doc.outerHtml()

  override def close(): Unit = {
    url = "about:blank"
    doc = JsoupWebDriver.emptyDoc
  }

  override def quit(): Unit = close()

  override def getWindowHandles: util.Set[String] = new util.HashSet[String]

  override def getWindowHandle: String = ""

  override def switchTo(): WebDriver.TargetLocator = ???

  override def navigate(): WebDriver.Navigation = ???

  override def manage(): WebDriver.Options = ???
}

object JsoupWebDriver {
  private lazy val emptyDoc: Document = Jsoup.parse("<html></html>")

  def create(capabilities: Capabilities): RoboBrowser = {
    new RoboBrowser(capabilities) {
      override type Driver = JsoupWebDriver

      override def sessionId: String = "Jsoup"

      override protected def createWebDriver(options: ChromeOptions): JsoupWebDriver = new JsoupWebDriver()
    }
  }

  def by2CSS(by: selenium.By): String = {
    val s = by.toString
    val index = s.indexOf(':')
    val `type` = s.substring(3, index)
    val v = s.substring(index + 1).trim
    `type` match {
      case "id" => s"#$v"
      case "name" => s"[name='$v']"
      case "tagName" => v
      case "className" => s".$v"
      case "cssSelector" => v
      case _ => throw new RuntimeException(s"${`type`} is not supported")
    }
  }

  def main(args: Array[String]): Unit = {
    val browser = RoboBrowser.Jsoup.create()
    browser.load(url"https://google.com")
    scribe.info(s"Title: ${browser.title}")
    val input = browser.oneBy(By.css("[name=\"q\"]"))
    scribe.info(s"Input: $input")
  }
}