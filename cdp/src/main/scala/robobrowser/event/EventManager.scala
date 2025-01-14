package robobrowser.event

import fabric.io.JsonFormatter
import robobrowser.comm.WSResponse

trait EventManager {
  val debug: Boolean = false

  private[event] var channels = Map.empty[String, EventChannel[_ <: Event]]

  val event: Events = Events(this)

  def fire(response: WSResponse): Unit = channels.get(response.method.get) match {
    case Some(c) => c.fire(response.params)
    case None => scribe.warn(s"No channel associated with method: ${response.method.get}\n${JsonFormatter.Default(response.params)}")
  }
}