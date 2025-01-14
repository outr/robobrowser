package robobrowser.event

import reactify.Channel

case class NetworkEvents(e: Events) {
  val policyUpdated: Channel[PolicyUpdatedEvent] = e.channel("Network.policyUpdated")
  val requestWillBeSent: Channel[RequestWillBeSentEvent] = e.channel("Network.requestWillBeSent")
  val responseReceived: Channel[ResponseReceivedEvent] = e.channel("Network.responseReceived")
  val dataReceived: Channel[DataReceivedEvent] = e.channel("Network.dataReceived")
  val loadingFinished: Channel[LoadingFinishedEvent] = e.channel("Network.loadingFinished")
  val requestWillBeSentExtraInfo: Channel[RequestWillBeSentExtraInfoEvent] = e.channel("Network.requestWillBeSentExtraInfo")
  val resourceChangedPriority: Channel[ResourceChangedPriorityEvent] = e.channel("Network.resourceChangedPriority")
  val responseReceivedExtraInfo: Channel[ResponseReceivedExtraInfoEvent] = e.channel("Network.responseReceivedExtraInfo")
  val requestServedFromCache: Channel[RequestServedFromCacheEvent] = e.channel("Network.requestServedFromCache")
  val webSocketCreated: Channel[WebSocketCreatedEvent] = e.channel("Network.webSocketCreated")
  val loadingFailed: Channel[LoadingFailedEvent] = e.channel("Network.loadingFailed")
}
