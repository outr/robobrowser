package robobrowser.event

import fabric.io.JsonFormatter
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
  event.runtime

  object event extends Channel[Event] {
    object target {
      val attachedToTarget: Channel[AttachedToTargetEvent] = channel("Target.attachedToTarget")
    }
    object network {
      val policyUpdated: Channel[PolicyUpdatedEvent] = channel("Network.policyUpdated")
      val requestWillBeSent: Channel[RequestWillBeSentEvent] = channel("Network.requestWillBeSent")
      val responseReceived: Channel[ResponseReceivedEvent] = channel("Network.responseReceived")
      val dataReceived: Channel[DataReceivedEvent] = channel("Network.dataReceived")
      val loadingFinished: Channel[LoadingFinishedEvent] = channel("Network.loadingFinished")
      val requestWillBeSentExtraInfo: Channel[RequestWillBeSentExtraInfoEvent] = channel("Network.requestWillBeSentExtraInfo")
      val resourceChangedPriority: Channel[ResourceChangedPriorityEvent] = channel("Network.resourceChangedPriority")
      val responseReceivedExtraInfo: Channel[ResponseReceivedExtraInfoEvent] = channel("Network.responseReceivedExtraInfo")
      val requestServedFromCache: Channel[RequestServedFromCacheEvent] = channel("Network.requestServedFromCache")
      val webSocketCreated: Channel[WebSocketCreatedEvent] = channel("Network.webSocketCreated")
      val loadingFailed: Channel[LoadingFailedEvent] = channel("Network.loadingFailed")
    }
    object dom {
      val documentUpdated: Channel[DocumentUpdatedEvent] = channel("DOM.documentUpdated")
    }
    object page {
      val domContentEventFired: Channel[FrameAttachedEvent] = channel("Page.domContentEventFired")
      val frameAttached: Channel[FrameAttachedEvent] = channel("Page.frameAttached")
      val frameDetached: Channel[FrameDetachedEvent] = channel("Page.frameDetached")
      val frameNavigated: Channel[FrameNavigatedEvent] = channel("Page.frameNavigated")
      val frameRequestedNavigation: Channel[FrameRequestedNavigationEvent] = channel("Page.frameRequestedNavigation")
      val frameScheduledNavigation: Channel[FrameScheduledNavigationEvent] = channel("Page.frameScheduledNavigation")
      val frameClearedScheduledNavigation: Channel[FrameClearedScheduledNavigationEvent] = channel("Page.frameClearedScheduledNavigation")
      val frameStartedLoading: Channel[FrameStartedLoadingEvent] = channel("Page.frameStartedLoading")
      val frameStoppedLoading: Channel[FrameStoppedLoadingEvent] = channel("Page.frameStoppedLoading")
      val frameSubtreeWillBeDetached: Channel[FrameSubtreeWillBeDetachedEvent] = channel("Page.frameSubtreeWillBeDetached")
      val javascriptDialogOpening: Channel[JavascriptDialogOpeningEvent] = channel("Page.javascriptDialogOpening")
      val javascriptDialogClosed: Channel[JavascriptDialogClosedEvent] = channel("Page.javascriptDialogClosed")
      val lifecycle: Channel[LifecycleEvent] = channel("Page.lifecycleEvent")
      val loadEventFired: Channel[LoadEventFiredEvent] = channel("Page.loadEventFired")
      val navigatedWithinDocument: Channel[NavigatedWithinDocumentEvent] = channel("Page.navigatedWithinDocument")
    }
    object inspector {
      val detached: Channel[DetachedEvent] = channel("Inspector.detached")
    }
    object runtime {
      val executionContextCreated: Channel[ExecutionContextCreatedEvent] = channel("Runtime.executionContextCreated")
      val executionContextsCleared: Channel[ExecutionContextsClearedEvent] = channel("Runtime.executionContextsCleared")
    }
  }

  protected def channel[E <: Event](method: String)(implicit rw: RW[E]): Channel[E] = synchronized {
    val c = EventChannel[E](rw)
    channels += method -> c
    c
  }

  def fire(response: WSResponse): Unit = channels.get(response.method.get) match {
    case Some(c) => c.fire(response.params)
    case None => scribe.warn(s"No channel associated with method: ${response.method.get}\n${JsonFormatter.Default(response.params)}")
  }
}