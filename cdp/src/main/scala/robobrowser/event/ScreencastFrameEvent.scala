package robobrowser.event

import fabric.rw._

/** Metadata carried with each CDP `Page.screencastFrame`: the page's scroll
  * offset and the device viewport the frame was rendered at — needed to map
  * the displayed image back to page/viewport coordinates for input forwarding. */
case class ScreencastFrameMetadata(offsetTop: Double = 0.0,
                                   pageScaleFactor: Double = 1.0,
                                   deviceWidth: Double = 0.0,
                                   deviceHeight: Double = 0.0,
                                   scrollOffsetX: Double = 0.0,
                                   scrollOffsetY: Double = 0.0,
                                   timestamp: Option[Double] = None)

object ScreencastFrameMetadata {
  implicit val rw: RW[ScreencastFrameMetadata] = RW.gen
}

/** A single streamed frame from CDP `Page.startScreencast`. `data` is the
  * base64-encoded image (JPEG or PNG per the requested format). Each frame
  * MUST be acked (`Page.screencastFrameAck` with its `sessionId`) or Chrome
  * stops sending — [[robobrowser.Screencast]] does that automatically. */
case class ScreencastFrameEvent(data: String,
                                metadata: ScreencastFrameMetadata,
                                sessionId: Int) extends Event

object ScreencastFrameEvent {
  implicit val rw: RW[ScreencastFrameEvent] = RW.gen
}
