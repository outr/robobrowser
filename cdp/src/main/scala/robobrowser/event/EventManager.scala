package robobrowser.event

import fabric.io.JsonFormatter
import rapid._
import rapid.logger._
import robobrowser.comm.WSResponse

trait EventManager {
  val debug: Boolean = false

  private[event] var channels = Map.empty[String, EventChannel[? <: Event]]

  val event: Events = Events(this)

  def fire(response: WSResponse): Unit = channels.get(response.method.get) match {
    case Some(c) => Task(c.fire(response.params)).logErrors.start()
    case None => scribe.warn(s"No channel associated with method: ${response.method.get}\n${JsonFormatter.Default(response.params)}")
  }
}