package robobrowser.dom

import fabric.Json
import fabric.rw.Asable
import rapid.Task
import robobrowser.RoboBrowser

case class DOM(browser: RoboBrowser) {
  def document: Task[Document] = browser.send("DOM.getDocument").map(_.result("root").as[Document])
}