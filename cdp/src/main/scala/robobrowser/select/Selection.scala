package robobrowser.select

import fabric.Json
import rapid.Task
import robobrowser.RoboBrowser

case class Selection(browser: RoboBrowser, selector: Selector) {
  private lazy val cssSelectorOne: String = s"document.querySelector(\"${selector.query}\")"
  private lazy val cssSelectorAll: String = s"document.querySelectorAll(\"${selector.query}\")"

  def count: Task[Int] = browser.eval(s"$cssSelectorAll.length").map { json =>
    json("result")("value").asInt
  }

  def focus: Task[Unit] = browser.eval(s"$cssSelectorOne.focus()").unit

  def click: Task[Unit] = browser.eval(s"$cssSelectorOne.click()").unit

  def submit: Task[Unit] = browser.eval(s"$cssSelectorOne.submit()").unit

  def value: Task[Json] = browser.eval(s"$cssSelectorOne.value").map { json =>
    json("result")("value")
  }
  def value(json: Json): Task[Unit] = browser.callFunction(
      s"""const elements = $cssSelectorAll;
         |elements.forEach((el) => {
         |  el.value = obj1;
         |  el.dispatchEvent(new Event('input', { bubbles: true }));
         |});""".stripMargin, json)
    .unit

  def getAttribute(name: String): Task[Vector[String]] = browser.eval(s"Array.from($cssSelectorAll, n => n.getAttribute('$name'));")
    .map(_("result")("value").asVector.map(_.asString))

  def hasAttribute(name: String): Task[Vector[Boolean]] = browser.eval(s"Array.from($cssSelectorAll, n => n.hasAttribute('$name'));")
    .map(_("result")("value").asVector.map(_.asBoolean))

  def outerHTML: Task[Vector[String]] = browser.eval(s"Array.from($cssSelectorAll, n => n.outerHTML);")
    .map(_("result")("value").asVector.map(_.asString))

  def innerHTML: Task[Vector[String]] = browser.eval(s"Array.from($cssSelectorAll, n => n.innerHTML);")
    .map(_("result")("value").asVector.map(_.asString))

  def innerText: Task[Vector[String]] = browser.eval(s"Array.from($cssSelectorAll, n => n.innerText);")
    .map(_("result")("value").asVector.map(_.asString))
}
