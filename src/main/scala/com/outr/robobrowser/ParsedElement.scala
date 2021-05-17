package com.outr.robobrowser

import org.jsoup.nodes.{Element, Node, TextNode}
import org.jsoup.select.Elements

import scala.language.implicitConversions

import scala.jdk.CollectionConverters._

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