package com.outr.robobrowser.event

import cats.effect.IO
import com.outr.robobrowser.{Context, RoboBrowser, SeleniumWebElement, WebElement}
import fabric.Json
import fabric.io.JsonParser
import fabric.rw._

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._

class EventManager(browser: RoboBrowser) {
  private var queueMap = Map.empty[String, EventManagerQueue[_]]

  init()

  private def init(): Unit = {
    val input = getClass.getClassLoader.getResourceAsStream("event-manager.js")
    browser.executeInputStream(input)
  }

  def queue[T](key: String)(implicit rw: RW[T]): EventManagerQueue[T] = synchronized {
    assert(!queueMap.contains(key), s"Queue already exists for $key")
    val q = new EventManagerQueue[T](key, rw, browser)
    queueMap += key -> q
    q
  }

  def keyEventQueue(key: String, element: Option[WebElement]): EventManagerQueue[KeyEvent] = {
    val q = queue[KeyEvent](key)
    browser.execute(
      """let el = arguments[1];
        |if (!el) el = document;
        |el.addEventListener('keyup', (e) => {
        |  window.roboEvents.enqueueJson(arguments[0], {
        |    'code': e.code,
        |    'key': e.key,
        |    'repeat': e.repeat,
        |    'composing': e.isComposing,
        |    'shift': e.shiftKey,
        |    'alt': e.altKey,
        |    'ctrl': e.ctrlKey,
        |    'meta': e.metaKey
        |  }, e.target);
        |});""".stripMargin, key, element.orNull)
    q
  }

  def monitor(every: FiniteDuration = 1.second): IO[Unit] = {
    IO(check()).flatMap { _ =>
      IO.sleep(every)
    }.flatMap { _ =>
      monitor(every)
    }
  }

  def check(): Unit = {
    val events = get()
    events.foreach { evt =>
      try {
        val queue = queueMap(evt.key)
        queue.fire(evt)
      } catch {
        case t: Throwable => scribe.error(s"Error handling queue for: $evt ", t)
      }
    }
  }

  private def get(): List[Event[Json]] = {
    browser.executeTyped[java.util.ArrayList[java.util.Map[String, AnyRef]]]("return window.roboEvents.get();")
      .asScala
      .toList
      .map(_.asScala.toMap)
      .map { map =>
        val key = map("key").asInstanceOf[String]
        val content = map("content").asInstanceOf[String]
        val json = JsonParser(content)
        val element = map
          .get("element")
          .map(_.asInstanceOf[org.openqa.selenium.WebElement])
          .map { e =>
            new SeleniumWebElement(e, Context.Browser, browser)
          }
        Event(key, json, element)
      }
  }

  // TODO: Support monitor for checking status and firing events

  // TODO: Support key events and mouse events
}