package robobrowser.event

import fabric.Json
import fabric.rw._
import reactify.Channel

case class EventChannel[E <: Event](rw: RW[E]) extends Channel[E] {
  def fire(json: Json): Unit = if (reactions().nonEmpty) {    // Only fire the event if someone is listening
    val e = rw.write(json)
    this @= e
  }
}
