package com.outr.robobrowser

case class By(value: String, `type`: ByType, context: Context = Context.Browser)

object By {
  def id(value: String, context: Context = Context.Browser): By = By(value, ByType.Id, context)
  def linkText(value: String, context: Context = Context.Browser): By = By(value, ByType.`Link Text`, context)
  def partialLinkText(value: String, context: Context = Context.Browser): By = By(value, ByType.`Partial Link Text`, context)
  def name(value: String, context: Context = Context.Browser): By = By(value, ByType.Name, context)
  def tagName(value: String, context: Context = Context.Browser): By = By(value, ByType.`Tag Name`, context)
  def xPath(value: String, context: Context = Context.Browser): By = By(value, ByType.XPath, context)
  def className(value: String, context: Context = Context.Browser): By = By(value, ByType.`Class Name`, context)
  def css(value: String, context: Context = Context.Browser): By = By(value, ByType.`CSS Selector`, context)
}