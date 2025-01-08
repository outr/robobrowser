package com.outr.robobrowser.browser.jsoup

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.openqa.selenium
import org.openqa.selenium.WebDriver

import java.util

class JsoupWebDriver(userAgent: String) extends WebDriver {
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
}