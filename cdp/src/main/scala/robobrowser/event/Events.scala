package robobrowser.event

import fabric.rw._
import reactify.Channel

case class Events(m: EventManager) {
  val target: TargetEvents = TargetEvents(this)
  val network: NetworkEvents = NetworkEvents(this)
  val dom: DOMEvents = DOMEvents(this)
  val page: PageEvents = PageEvents(this)
  val inspector: InspectorEvents = InspectorEvents(this)
  val runtime: RuntimeEvents = RuntimeEvents(this)
  
  private[event] def channel[E <: Event](method: String)(implicit rw: RW[E]): Channel[E] = synchronized {
    val c = EventChannel[E](rw)
    m.channels += method -> c
    c
  }
}