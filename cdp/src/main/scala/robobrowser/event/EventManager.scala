package robobrowser.event

import fabric.io.JsonFormatter
import rapid._
import rapid.logger._
import reactify.Channel
import robobrowser.comm.WSResponse

trait EventManager {
  val debug: Boolean = false

  private[event] var channels = Map.empty[String, EventChannel[? <: Event]]

  val event: Events = Events(this)

  /** Every protocol event the browser sends, typed channel or not, so an event this library does not model yet can
    * still be observed (filter on `method`, read `params`). */
  val anyEvent: Channel[WSResponse] = Channel[WSResponse]

  def fire(response: WSResponse): Unit = {
    anyEvent @= response
    channels.get(response.method.get) match {
      case Some(c) => Task(c.fire(response.params)).logErrors.start()
      case None if anyEvent.reactions().isEmpty =>
        scribe.warn(s"No channel associated with method: ${response.method.get}\n${JsonFormatter.Default(response.params)}")
      case None => ()
    }
  }
}
