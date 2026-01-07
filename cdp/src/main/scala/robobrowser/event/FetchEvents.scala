package robobrowser.event

import reactify.Channel

case class FetchEvents(events: Events) {
  val authRequired: Channel[FetchAuthRequired] = events.channel[FetchAuthRequired]("Fetch.authRequired")
  val requestPaused: Channel[FetchRequestPausedEvent] = events.channel[FetchRequestPausedEvent]("Fetch.requestPaused")
}