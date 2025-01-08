package robobrowser.event

import fabric.rw.RW
import reactify.Channel
import robobrowser.comm.WSResponse

trait EventManager {
  private var channels = Map.empty[String, EventChannel[_ <: Event]]

  // Make sure all events are loaded
  // TODO: Ignore unhandled events in the future
  event.target
  event.network
  event.dom
  event.page
  event.inspector

  object event extends Channel[Event] {
    object target {
      val attachedToTarget: Channel[AttachedToTargetEvent] = channel[AttachedToTargetEvent]("Target.attachedToTarget")
    }
    object network {
      val policyUpdated: Channel[PolicyUpdatedEvent] = channel[PolicyUpdatedEvent]("Network.policyUpdated")
      val requestWillBeSent: Channel[RequestWillBeSentEvent] = channel[RequestWillBeSentEvent]("Network.requestWillBeSent")
      val responseReceived: Channel[ResponseReceivedEvent] = channel[ResponseReceivedEvent]("Network.responseReceived")
      val dataReceived: Channel[DataReceivedEvent] = channel[DataReceivedEvent]("Network.dataReceived")
      val loadingFinished: Channel[LoadingFinishedEvent] = channel[LoadingFinishedEvent]("Network.loadingFinished")
      val requestWillBeSentExtraInfo: Channel[RequestWillBeSentExtraInfoEvent] = channel[RequestWillBeSentExtraInfoEvent]("Network.requestWillBeSentExtraInfo")
      val resourceChangedPriority: Channel[ResourceChangedPriorityEvent] = channel[ResourceChangedPriorityEvent]("Network.resourceChangedPriority")
      val responseReceivedExtraInfo: Channel[ResponseReceivedExtraInfoEvent] = channel[ResponseReceivedExtraInfoEvent]("Network.responseReceivedExtraInfo")
      val requestServedFromCache: Channel[RequestServedFromCacheEvent] = channel[RequestServedFromCacheEvent]("Network.requestServedFromCache")
      val webSocketCreated: Channel[WebSocketCreatedEvent] = channel[WebSocketCreatedEvent]("Network.webSocketCreated")
      val loadingFailed: Channel[LoadingFailedEvent] = channel[LoadingFailedEvent]("Network.loadingFailed")
    }
    object dom {
      val documentUpdated: Channel[DocumentUpdatedEvent] = channel[DocumentUpdatedEvent]("DOM.documentUpdated")
    }
    object page {
      val frameAttached: Channel[FrameAttachedEvent] = channel[FrameAttachedEvent]("Page.frameAttached")
      val frameDetached: Channel[FrameDetachedEvent] = channel[FrameDetachedEvent]("Page.frameDetached")
      val frameNavigated: Channel[FrameNavigatedEvent] = channel[FrameNavigatedEvent]("Page.frameNavigated")
      val frameRequestedNavigation: Channel[FrameRequestedNavigationEvent] = channel[FrameRequestedNavigationEvent]("Page.frameRequestedNavigation")
      val frameScheduledNavigation: Channel[FrameScheduledNavigationEvent] = channel[FrameScheduledNavigationEvent]("Page.frameScheduledNavigation")
      val frameClearedScheduledNavigation: Channel[FrameClearedScheduledNavigationEvent] = channel[FrameClearedScheduledNavigationEvent]("Page.frameClearedScheduledNavigation")
      val frameStartedLoading: Channel[FrameStartedLoadingEvent] = channel[FrameStartedLoadingEvent]("Page.frameStartedLoading")
      val frameStoppedLoading: Channel[FrameStoppedLoadingEvent] = channel[FrameStoppedLoadingEvent]("Page.frameStoppedLoading")
      val frameSubtreeWillBeDetached: Channel[FrameSubtreeWillBeDetachedEvent] = channel[FrameSubtreeWillBeDetachedEvent]("Page.frameSubtreeWillBeDetached")
      val domContentEventFired: Channel[FrameAttachedEvent] = channel[FrameAttachedEvent]("Page.domContentEventFired")
      val loadEventFired: Channel[LoadEventFiredEvent] = channel[LoadEventFiredEvent]("Page.loadEventFired")
      val navigatedWithinDocumentEvent: Channel[NavigatedWithinDocumentEvent] = channel[NavigatedWithinDocumentEvent]("Page.navigatedWithinDocument")
    }
    object inspector {
      val detached: Channel[DetachedEvent] = channel[DetachedEvent]("Inspector.detached")
    }
  }

  protected def channel[E <: Event](method: String)(implicit rw: RW[E]): Channel[E] = synchronized {
    val c = EventChannel[E](rw)
    channels += method -> c
    c
  }

  def fire(response: WSResponse): Unit = channels.get(response.method.get) match {
    case Some(c) => c.fire(response.params)
    case None => scribe.warn(s"No channel associated with method: ${response.method.get}")
  }
}