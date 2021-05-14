package com.outr.robobrowser

import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element, Node, TextNode}
import org.jsoup.select.Elements
import org.openqa.selenium.By

import scala.jdk.CollectionConverters._
import scala.language.implicitConversions

class WebElement(e: org.openqa.selenium.WebElement) extends AbstractElement {
  override def by(by: By): List[WebElement] = e.findElements(by).asScala.toList.map(new WebElement(_))

  def click(): Unit = e.click()
  def submit(): Unit = e.submit()

  def tagName: String = e.getTagName
  def text: String = e.getText
  def attribute(name: String): String = e.getAttribute(name)
  def classes: Set[String] = attribute("class").split(' ').toSet

  def sendInput(text: String): Unit = {
    e.click()
    e.sendKeys(text)
  }

  def parsed(): ParsedElement = new ParsedElement(Jsoup.parseBodyFragment(outerHTML).body().child(0))

  override def outerHTML: String = e.getAttribute("outerHTML")

  override def innerHTML: String = e.getAttribute("innerHTML")

  override def toString: String = outerHTML
}

class ParsedElement(node: Node) {
  def nodeName: String = node.nodeName()
  lazy val attributes: Map[String, String] = node.attributes().asScala.toList.map(a => a.getKey -> a.getValue).toMap
  lazy val children: List[ParsedElement] = node.childNodes()
  lazy val outerHTML: String = node.outerHtml()
  lazy val innerHTML: String = children.map(_.outerHTML).mkString("")
  lazy val text: String = node match {
    case e: Element => e.text()
    case t: TextNode => t.text()
    case _ => ""
  }

  private implicit def elements2List(elements: Elements): List[ParsedElement] = elements.asScala.toList.map(new ParsedElement(_))
  private implicit def nodes2List(nodes: java.util.List[Node]): List[ParsedElement] = nodes.asScala.toList.map(new ParsedElement(_))

  def by(cssSelector: String): List[ParsedElement] = node match {
    case e: Element => e.select(cssSelector)
    case _ => Nil
  }

  def isElement: Boolean = node.isInstanceOf[Element]
  def isText: Boolean = node.isInstanceOf[TextNode]

  override def toString: String = outerHTML
}