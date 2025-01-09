package robobrowser.select

import fabric.Json
import fabric.io.JsonFormatter
import rapid.Task
import robobrowser.RoboBrowser

case class Selection(browser: RoboBrowser, selector: Selector) {
  private lazy val cssSelectorOne: String = s"document.querySelector('${selector.query}')"
  private lazy val cssSelectorAll: String = s"document.querySelectorAll('${selector.query}')"

  def count: Task[Int] = browser.eval(s"$cssSelectorAll.length").map { json =>
    json("result")("value").asInt
  }

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
}
