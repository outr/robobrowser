package robobrowser.stream

/** Configuration for a WebRTC stream of a browser's virtual display.
  *
  * `width`/`height` and `maxWidth`/`maxHeight` are different knobs and both
  * apply:
  *
  *  - **`width`/`height` — the render target.** The page is laid out at exactly
  *    this size (CDP device-metrics emulation) and exactly this rectangle of the
  *    virtual display is captured. Any aspect ratio is honoured verbatim: a
  *    `390x844` target streams a portrait, mobile-layout page with no
  *    letterboxing. Unset means "render and capture the whole display", the
  *    historical behaviour. The target must fit within the session's virtual
  *    display; a larger one is attempted as a display resize first and fails
  *    with [[robobrowser.display.DisplayResizeUnsupportedException]] when the X
  *    server won't resize.
  *  - **`maxWidth`/`maxHeight` — the encoder cap.** Purely an encode-time
  *    downscale of whatever was rendered, aspect preserved and never upscaling.
  *    The page still lays out at the render target; only the transmitted frame
  *    shrinks.
  *
  * So `width = 1280, height = 800, maxWidth = 640` renders a 1280x800 page and
  * transmits it at 640x400.
  *
  * @param codec           video codec (H264 has universal decode support; AV1
  *                        support depends on installed GStreamer encoders)
  * @param maxBitrate      encoder bitrate cap in bits/second (fixed in v1 —
  *                        congestion-driven adaptation is a planned follow-up)
  * @param maxFps          frame rate cap
  * @param width           optional render/capture width; defaults to the display
  * @param height          optional render/capture height; defaults to the display
  * @param maxWidth        optional encode-time downscale bound (aspect preserved)
  * @param maxHeight       optional encode-time downscale bound (aspect preserved)
  * @param showPointer     composite the cursor into the stream (the client does
  *                        not render a local cursor)
  * @param stunServer      STUN server URI (`stun://host:port`) for ICE
  * @param turnServers     optional TURN URIs (`turn://user:pass@host:port`)
  * @param encoderOverride force a specific GStreamer encoder element (e.g.
  *                        "x264enc") instead of the hardware-first probe order
  * @param routeInput      automatically dispatch DataChannel input events into
  *                        the browser via CDP */
case class StreamConfig(codec: Codec = Codec.H264,
                        maxBitrate: Int = 8_000_000,
                        maxFps: Int = 60,
                        width: Option[Int] = None,
                        height: Option[Int] = None,
                        maxWidth: Option[Int] = None,
                        maxHeight: Option[Int] = None,
                        showPointer: Boolean = true,
                        stunServer: Option[String] = Some("stun://stun.l.google.com:19302"),
                        turnServers: List[String] = Nil,
                        encoderOverride: Option[String] = None,
                        routeInput: Boolean = true) {

  /** The requested render target, when either dimension was set. A partial
    * request inherits the missing dimension from the display. */
  def requestedTarget(display: RenderSize): Option[RenderSize] = (width, height) match {
    case (None, None) => None
    case (w, h) => Some(RenderSize.even(w.getOrElse(display.width), h.getOrElse(display.height)))
  }
}

sealed trait Codec {
  def name: String
}

object Codec {
  case object H264 extends Codec {
    val name: String = "H264"
  }
  case object AV1 extends Codec {
    val name: String = "AV1"
  }

  import fabric.rw._
  implicit val rw: RW[Codec] = RW.string[Codec](
    _.name,
    {
      case "H264" => H264
      case "AV1" => AV1
      case other => throw new RuntimeException(s"Unknown codec: $other")
    }
  )
}
