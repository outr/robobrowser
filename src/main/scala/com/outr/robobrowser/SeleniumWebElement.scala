package com.outr.robobrowser

import org.jsoup.Jsoup
import org.openqa.selenium.{By, OutputType, TakesScreenshot}

import scala.jdk.CollectionConverters._

class SeleniumWebElement(private val e: org.openqa.selenium.WebElement,
                         val context: Context,
                         protected val browser: RoboBrowser) extends WebElement {
  private def l2l(list: java.util.List[org.openqa.selenium.WebElement]): List[WebElement] = list
    .asScala
    .toList
    .map(new SeleniumWebElement(_, context, browser))

  override def by(by: By): List[WebElement] = l2l(e.findElements(by))

  override def children: List[WebElement] = l2l(browser.executeTyped[java.util.List[org.openqa.selenium.WebElement]](
    script = "return arguments[0].children;",
    args = e
  ))

  override def capture(): Array[Byte] = e.asInstanceOf[TakesScreenshot].getScreenshotAs(OutputType.BYTES)

  override def click(): WebElement = browser.withDriverAndContext(context) { _ =>
    e.click()
    this
  }

  override def submit(): WebElement = browser.withDriverAndContext(context) { _ =>
    e.submit()
    this
  }

  override def tagName: String = browser.withDriverAndContext(context) { _ =>
    e.getTagName
  }

  override def text: String = browser.withDriverAndContext(context) { _ =>
    e.getText
  }

  override def attribute(name: String): String = browser.withDriverAndContext(context) { _ =>
    e.getAttribute(name)
  }

  override def attribute(name: String, value: Any): WebElement = {
    browser.execute("arguments[0].setAttribute(arguments[1], arguments[2])", e, name, value.asInstanceOf[AnyRef])
    this
  }

  override def style(name: String): Any = browser.execute(s"return arguments[0].style.$name;", e)

  override def style(name: String, value: Any): WebElement = {
    browser.execute(s"arguments[0].style.$name = arguments[1]", e, value.asInstanceOf[AnyRef])
    this
  }

  override def classes: Set[String] = attribute("class").split(' ').toSet

  override def sendInput(text: String): Unit = {
//    e.click()     // TODO: Verify if this is needed
    e.sendKeys(text)
  }

  override def parsed(): ParsedElement = new ParsedElement(Jsoup.parseBodyFragment(outerHTML).body().child(0))

  override def outerHTML: String = browser.executeTyped[String]("return arguments[0].outerHTML;", e)

  override def innerHTML: String = e.getAttribute("innerHTML")

  override def toString: String = if (context == Context.Native) {
    e.toString
  } else {
    outerHTML
  }
}

object SeleniumWebElement {
  def underlying(element: WebElement): org.openqa.selenium.WebElement = element.asInstanceOf[SeleniumWebElement].e
}