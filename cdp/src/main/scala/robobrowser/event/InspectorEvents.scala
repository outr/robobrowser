package robobrowser.event

import reactify.Channel

case class InspectorEvents(e: Events) {
  val detached: Channel[DetachedEvent] = e.channel("Inspector.detached")
}
