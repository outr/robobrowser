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

  object queue {
    def apply[T](key: String)(implicit rw: RW[T]): EventManagerQueue[T] = synchronized {
      assert(!queueMap.contains(key), s"Queue already exists for $key")
      val q = new EventManagerQueue[T](key, rw, browser)
      queueMap += key -> q
      q
    }

    private def events[T](key: String, element: Option[WebElement], eventType: String, toJson: String)
                         (implicit rw: RW[T]): EventManagerQueue[T] = {
      val q = queue[T](key)
      browser.execute(
       s"""let el = arguments[1];
          |if (!el) el = document;
          |el.addEventListener('$eventType', (e) => {
          |  window.roboEvents.enqueueJson(arguments[0], $toJson, e.target);
          |});""".stripMargin, key, element.orNull)
      q
    }

    object key {
      private lazy val js: String =
        """{
          |  'code': e.code,
          |  'key': e.key,
          |  'repeat': e.repeat,
          |  'composing': e.isComposing,
          |  'shift': e.shiftKey,
          |  'alt': e.altKey,
          |  'ctrl': e.ctrlKey,
          |  'meta': e.metaKey
          |}""".stripMargin

      def down(key: String, element: Option[WebElement]): EventManagerQueue[KeyEvent] =
        events[KeyEvent](key, element, "keyup", js)

      def up(key: String, element: Option[WebElement]): EventManagerQueue[KeyEvent] =
        events[KeyEvent](key, element, "keyup", js)
    }

    object pointer {
      private lazy val js: String =
        """{
          |  'button': e.button,
          |  'buttons': e.buttons,
          |  'x': e.x,
          |  'y': e.y,
          |  'clientX': e.clientX,
          |  'clientY': e.clientY,
          |  'movementX': e.movementX,
          |  'movementY': e.movementY,
          |  'offsetX': e.offsetX,
          |  'offsetY': e.offsetY,
          |  'pageX': e.pageX,
          |  'pageY': e.pageY,
          |  'screenX': e.screenX,
          |  'screenY': e.screenY,
          |  'pointerId': e.pointerId,
          |  'width': e.width,
          |  'height': e.height,
          |  'pressure': e.pressure,
          |  'tangentialPressure': e.tangentialPressure,
          |  'tiltX': e.tiltX,
          |  'tiltY': e.tiltY,
          |  'twist': e.twist,
          |  'pointerType': e.pointerType,
          |  'primary': e.isPrimary,
          |  'shift': e.shiftKey,
          |  'alt': e.altKey,
          |  'ctrl': e.ctrlKey,
          |  'meta': e.metaKey
          |}""".stripMargin

      def over(key: String, element: Option[WebElement]): EventManagerQueue[PointerEvent] =
        events[PointerEvent](key, element, "pointerover", js)

      def enter(key: String, element: Option[WebElement]): EventManagerQueue[PointerEvent] =
        events[PointerEvent](key, element, "pointerenter", js)

      def down(key: String, element: Option[WebElement]): EventManagerQueue[PointerEvent] =
        events[PointerEvent](key, element, "pointerdown", js)

      def move(key: String, element: Option[WebElement]): EventManagerQueue[PointerEvent] =
        events[PointerEvent](key, element, "pointermove", js)

      def up(key: String, element: Option[WebElement]): EventManagerQueue[PointerEvent] =
        events[PointerEvent](key, element, "pointerup", js)

      def cancel(key: String, element: Option[WebElement]): EventManagerQueue[PointerEvent] =
        events[PointerEvent](key, element, "pointercancel", js)

      def out(key: String, element: Option[WebElement]): EventManagerQueue[PointerEvent] =
        events[PointerEvent](key, element, "pointerout", js)

      def leave(key: String, element: Option[WebElement]): EventManagerQueue[PointerEvent] =
        events[PointerEvent](key, element, "pointerleave", js)

      object capture {
        def got(key: String, element: Option[WebElement]): EventManagerQueue[PointerEvent] =
          events[PointerEvent](key, element, "gotpointercapture", js)

        def lost(key: String, element: Option[WebElement]): EventManagerQueue[PointerEvent] =
          events[PointerEvent](key, element, "lostpointercapture", js)
      }
    }
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
}