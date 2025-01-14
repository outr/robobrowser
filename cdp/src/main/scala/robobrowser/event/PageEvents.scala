package robobrowser.event

import reactify.Channel

case class PageEvents(e: Events) {
  val backForwardCacheNotUsed: Channel[BackForwardCacheNotUsedEvent] = e.channel("Page.backForwardCacheNotUsed")
  val domContentEventFired: Channel[FrameAttachedEvent] = e.channel("Page.domContentEventFired")
  val downloadProgress: Channel[DownloadProgressEvent] = e.channel("Page.downloadProgress")
  val downloadWillBegin: Channel[DownloadWillBeginEvent] = e.channel("Page.downloadWillBegin")
  val frameAttached: Channel[FrameAttachedEvent] = e.channel("Page.frameAttached")
  val frameDetached: Channel[FrameDetachedEvent] = e.channel("Page.frameDetached")
  val frameNavigated: Channel[FrameNavigatedEvent] = e.channel("Page.frameNavigated")
  val frameRequestedNavigation: Channel[FrameRequestedNavigationEvent] = e.channel("Page.frameRequestedNavigation")
  val frameScheduledNavigation: Channel[FrameScheduledNavigationEvent] = e.channel("Page.frameScheduledNavigation")
  val frameClearedScheduledNavigation: Channel[FrameClearedScheduledNavigationEvent] = e.channel("Page.frameClearedScheduledNavigation")
  val frameResized: Channel[FrameResizedEvent] = e.channel("Page.frameResized")
  val frameStartedLoading: Channel[FrameStartedLoadingEvent] = e.channel("Page.frameStartedLoading")
  val frameStoppedLoading: Channel[FrameStoppedLoadingEvent] = e.channel("Page.frameStoppedLoading")
  val frameSubtreeWillBeDetached: Channel[FrameSubtreeWillBeDetachedEvent] = e.channel("Page.frameSubtreeWillBeDetached")
  val javascriptDialogOpening: Channel[JavascriptDialogOpeningEvent] = e.channel("Page.javascriptDialogOpening")
  val javascriptDialogClosed: Channel[JavascriptDialogClosedEvent] = e.channel("Page.javascriptDialogClosed")
  val lifecycle: Channel[LifecycleEvent] = e.channel("Page.lifecycleEvent")
  val loadEventFired: Channel[LoadEventFiredEvent] = e.channel("Page.loadEventFired")
  val navigatedWithinDocument: Channel[NavigatedWithinDocumentEvent] = e.channel("Page.navigatedWithinDocument")
  val windowOpen: Channel[WindowOpenEvent] = e.channel("Page.windowOpen")
}
