package robobrowser.event

import reactify.Channel

case class DOMEvents(e: Events) {
  val documentUpdated: Channel[DocumentUpdatedEvent] = e.channel("DOM.documentUpdated")
}
