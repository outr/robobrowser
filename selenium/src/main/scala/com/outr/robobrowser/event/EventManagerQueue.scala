package com.outr.robobrowser.event

import com.outr.robobrowser.{RoboBrowser, WebElement}
import fabric.Json
import fabric.io.JsonFormatter
import fabric.dsl._
import fabric.rw._

class EventManagerQueue[T](key: String, rw: RW[T], browser: RoboBrowser) {
  private var _listeners = List.empty[EventListener[T]]

  def enqueue(value: T, element: Option[WebElement] = None): Unit = browser
    .execute(
      "window.roboEvents.enqueueString(arguments[0], arguments[1], arguments[2]);",
      key, JsonFormatter.Compact(value.json(rw)), element.orNull
    )

  def +=(listener: EventListener[T]): Unit = synchronized {
    _listeners = listener :: _listeners
  }

  def listen(f: Event[T] => Unit): Unit = +=((evt: Event[T]) => f(evt))

  def fire(evt: Event[Json]): Unit = {
    val e = evt.copy[T](value = rw.write(evt.value))
    _listeners.foreach(_ (e))
  }
}
