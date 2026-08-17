package robobrowser.stream

import fabric.rw._

import scala.concurrent.duration.{DurationLong, FiniteDuration}

/** Point-in-time statistics for an active [[StreamSession]].
  *
  * @param encoder     the selected GStreamer encoder element (e.g. "vah264enc"
  *                    for VAAPI hardware encode, "x264enc" for the software
  *                    fallback)
  * @param codec       negotiated video codec
  * @param width       transmitted frame width — what a viewer's video element
  *                    reports. On a [[ResizeBehavior.Reconfigure]] branch this is
  *                    the render target with any maxWidth/maxHeight downscale
  *                    applied; on a [[ResizeBehavior.FixedCanvas]] branch it is
  *                    the encode canvas, which does not change for the session's
  *                    lifetime and which the render target sits bordered inside.
  * @param height      transmitted frame height
  * @param renderSize  the render target: the size the page lays out at and the
  *                    rectangle of the display captured. Authoritative for what
  *                    was asked for, independently of how it is transmitted.
  * @param resizeBehavior whether [[width]]/[[height]] follow [[renderSize]]
  *                    through a resize or stay put while the target is bordered
  *                    into them
  * @param fps         current encoded frame rate
  * @param bitrate     current send bitrate in bits/second
  * @param rtt         round-trip time to the viewer, when known
  * @param packetsLost RTP packets reported lost by the viewer
  * @param latency     estimated glass-to-glass latency from the measurement
  *                    harness, when the viewer reports it */
case class StreamStats(encoder: String,
                       codec: Codec,
                       width: Int,
                       height: Int,
                       renderSize: RenderSize,
                       resizeBehavior: ResizeBehavior,
                       fps: Double,
                       bitrate: Long,
                       rtt: Option[FiniteDuration],
                       packetsLost: Long,
                       latency: Option[FiniteDuration]) {

  /** Where [[renderSize]] sits inside the transmitted frame — the crop a
    * consumer that wants the content region alone applies. */
  def placement: RenderPlacement = RenderPlacement.fit(renderSize, RenderSize(width, height))
}

object StreamStats {
  implicit val durationRW: RW[FiniteDuration] = RW.long[FiniteDuration](_.toMillis, _.millis, None)
  implicit val rw: RW[StreamStats] = RW.gen
}
