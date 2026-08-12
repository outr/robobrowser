package robobrowser.stream

import fabric.rw._

import scala.concurrent.duration.{DurationLong, FiniteDuration}

/** Point-in-time statistics for an active [[StreamSession]].
  *
  * @param encoder     the selected GStreamer encoder element (e.g. "vah264enc"
  *                    for VAAPI hardware encode, "x264enc" for the software
  *                    fallback)
  * @param codec       negotiated video codec
  * @param width       encoded frame width (after any maxWidth/maxHeight downscale)
  * @param height      encoded frame height
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
                       fps: Double,
                       bitrate: Long,
                       rtt: Option[FiniteDuration],
                       packetsLost: Long,
                       latency: Option[FiniteDuration])

object StreamStats {
  implicit val durationRW: RW[FiniteDuration] = RW.long[FiniteDuration](_.toMillis, _.millis, None)
  implicit val rw: RW[StreamStats] = RW.gen
}
