package robobrowser.stream

/** Pure construction of the GStreamer launch description for a stream session
  * (kept side-effect free so it's testable without Gst.init). One pipeline per
  * session: display capture -> crop -> encode -> RTP -> webrtcbin.
  *
  * Design notes (element properties verified against GStreamer 1.28):
  *  - `use-damage=true`: frames are produced only when the page changes; a
  *    static page streams ~nothing, matching CDP screencast behavior.
  *  - Low latency everywhere: no B-frames, 2-second GOP with SPS/PPS on every
  *    IDR (`config-interval=-1`), zero-latency payloader aggregation, 40 ms
  *    jitterbuffer.
  *  - Keyframes on demand: webrtcbin's rtpbin converts client PLI/FIR into
  *    upstream force-key-unit events which all supported encoders honor;
  *    `min-force-key-unit-interval` (where available) throttles PLI storms.
  *  - Enum-valued properties are set in the launch string, not via GObject.set
  *    (GValue enum marshalling from the JVM is the flaky path).
  *  - The whole display is always captured and the render target is carved out
  *    of it by a named element anchored top-left, matching where CDP
  *    device-metrics emulation paints the emulated viewport. The cropped
  *    rectangle is the target exactly, so a branch that encodes the target
  *    itself never letterboxes; a fixed-canvas branch follows the crop with a
  *    `videobox` that borders it out to the canvas's aspect in real black.
  *  - A named capsfilter always pins the resolution the encoder receives, even
  *    when it already matches the crop. The crop insets are settable while the
  *    pipeline plays, which is what lets [[StreamSession.resize]] reconfigure in
  *    place: webrtcbin, its DTLS session, its ICE credentials and the input
  *    DataChannel are never disturbed, and no second offer is emitted.
  *  - Whether those caps move with the crop is the branch's [[ResizeBehavior]].
  *    Software encoding re-pins them per target; hardware branches hold a canvas
  *    fixed for the pipeline's lifetime and scale each target into it. */
private[stream] object PipelineBuilder {
  val WebRTCBinName: String = "webrtc"

  /** The display capture, named so its Xlib connection can be guarded against display loss. */
  val CaptureName: String = "capture-source"

  /** Identity element after capture whose handoff signal feeds fps accounting
    * and the latency harness (a wallclock stamp per captured frame). */
  val TapName: String = "capture-tap"

  /** Crops the full-display capture down to the render target. */
  val CropName: String = "capture-crop"

  /** Borders the cropped target out to the encode canvas's aspect ratio; present
    * only on a [[ResizeBehavior.FixedCanvas]] branch. */
  val BorderName: String = "capture-border"

  /** Pins the resolution handed to the encoder. */
  val ScaleCapsName: String = "encode-caps"

  /** The H.264 encoder, named so a resize can force an IDR out of it. */
  val EncoderName: String = "video-encoder"

  /** The rectangle of the display this session renders and captures: the
    * configured target when set, the whole display otherwise. */
  def targetSize(display: RenderSize, config: StreamConfig): RenderSize =
    config.requestedTarget(display).getOrElse(display)

  /** Encoded output size: the render target fit within maxWidth/maxHeight
    * (aspect preserved, never upscaled), rounded down to even dimensions as
    * H.264 4:2:0 requires. */
  def encodeSize(target: RenderSize, config: StreamConfig): RenderSize = {
    val scale = List(
      config.maxWidth.map(_.toDouble / target.width),
      config.maxHeight.map(_.toDouble / target.height),
      Some(1.0)
    ).flatten.min
    RenderSize.even((target.width * scale).toInt, (target.height * scale).toInt)
  }

  def fps(config: StreamConfig): Int = math.max(1, math.min(config.maxFps, 60))

  /** A hardware encoder allocates its surface pool per resolution, so re-pinning
    * a playing one is a driver decision: some accept it, some accept the caps
    * silently and keep emitting the resolution they were built with for the rest
    * of the session. Those branches encode into a canvas fixed when the pipeline
    * is built and scale each render target into it instead, which leaves the
    * encoder nothing to reconfigure. `x264enc` renegotiates its input cleanly and
    * keeps the true per-target reconfigure. */
  def resizeBehavior(encoder: String): ResizeBehavior = encoder match {
    case "vah264enc" | "vaapih264enc" | "nvh264enc" => ResizeBehavior.FixedCanvas
    case _ => ResizeBehavior.Reconfigure
  }

  /** The canvas a [[ResizeBehavior.FixedCanvas]] branch encodes into for its
    * whole lifetime. Every render target a session can reach is a rectangle of
    * its display, so bounding the canvas by the display (and by the configured
    * encode bounds, which apply to it exactly as they would to a target) makes it
    * large enough that no reachable target is scaled down into it. */
  def encodeCanvas(display: RenderSize, config: StreamConfig): RenderSize = RenderSize.even(
    math.min(display.width, config.maxWidth.getOrElse(display.width)),
    math.min(display.height, config.maxHeight.getOrElse(display.height))
  )

  /** The frame size an encoder branch transmits for `target`: the target itself
    * with the encode bounds applied when the branch re-pins its encoder, the
    * fixed canvas when it does not. */
  def encodedSize(encoder: String, display: RenderSize, target: RenderSize, config: StreamConfig): RenderSize =
    resizeBehavior(encoder) match {
      case ResizeBehavior.Reconfigure => encodeSize(target, config)
      case ResizeBehavior.FixedCanvas => encodeCanvas(display, config)
    }

  /** The border a fixed-canvas branch puts around the cropped target so the
    * scale into the canvas cannot reshape it, and so that border is a
    * `videobox`'s own black rather than whatever a driver initialises a scaler's
    * surface to. None where the encoder is re-pinned per target and the cropped
    * rectangle is already exactly what gets encoded. */
  def borderRegion(encoder: String, target: RenderSize, canvas: RenderSize): Option[CropRegion] =
    resizeBehavior(encoder) match {
      case ResizeBehavior.Reconfigure => None
      case ResizeBehavior.FixedCanvas => Some(CropRegion.border(target, canvas))
    }

  /** The caps [[ScaleCapsName]] pins for `size`, in the memory space and pixel
    * format its encoder branch consumes.
    *
    * A fixed-canvas branch also pins the pixel aspect ratio: without it a scaler
    * asked for a fixed width and height honours a differently-shaped input by
    * moving the pixel aspect instead of adding border, which reaches the viewer
    * as a stretched frame rather than a bordered one. */
  def encodeCaps(encoder: String, size: RenderSize): String = {
    val dimensions = s"width=${size.width},height=${size.height}"
    val square = ",pixel-aspect-ratio=1/1"
    encoder match {
      case "vah264enc" => s"video/x-raw(memory:VAMemory),format=NV12,$dimensions$square"
      case "vaapih264enc" => s"video/x-raw,$dimensions$square"
      case "nvh264enc" => s"video/x-raw,format=NV12,$dimensions$square"
      case _ => s"video/x-raw,format=I420,$dimensions"
    }
  }

  def description(displayName: String,
                  display: RenderSize,
                  config: StreamConfig,
                  encoder: String): String = {
    val target = targetSize(display, config)
    val encoded = encodedSize(encoder, display, target, config)
    val canvas = encodeCanvas(display, config)
    val frameRate = fps(config)
    val kbps = math.max(1, config.maxBitrate / 1000)
    val gop = frameRate * 2

    val webrtcTail = {
      val stun = config.stunServer.map(s => s" stun-server=$s").getOrElse("")
      val turn = config.turnServers.headOption.map(t => s" turn-server=$t").getOrElse("")
      s"webrtcbin name=$WebRTCBinName bundle-policy=max-bundle latency=40$stun$turn"
    }

    val head =
      s"ximagesrc display-name=$displayName use-damage=true show-pointer=${config.showPointer} name=$CaptureName ! " +
        s"video/x-raw,framerate=$frameRate/1 ! queue max-size-buffers=2 leaky=downstream ! " +
        s"identity name=$TapName signal-handoffs=true silent=true ! " +
        s"videocrop name=$CropName ${CropRegion(display, target).launchArgs}" +
        borderRegion(encoder, target, canvas)
          .map(region => s" ! videobox name=$BorderName fill=black ${region.launchArgs}")
          .getOrElse("")

    val scaleCaps = s"capsfilter name=$ScaleCapsName caps=${encodeCaps(encoder, encoded)}"

    val encodeTail = encoder match {
      case "vah264enc" =>
        // vapostproc scales on the GPU, so no videoscale on this branch. Its
        // input already carries the canvas's aspect, so add-borders only covers
        // the rounding residue — the visible border is the capture box's black
        s"videoconvert ! vapostproc add-borders=true ! $scaleCaps ! " +
          s"vah264enc name=$EncoderName rate-control=cbr bitrate=$kbps cpb-size=$kbps key-int-max=$gop " +
          "b-frames=0 ref-frames=1 target-usage=6 min-force-key-unit-interval=500000000 ! " +
          // Without a profile pin the encoder may offer a profile the browser
          // rejects, killing the whole bundle (the video m-line carries ICE)
          "video/x-h264,profile=constrained-baseline"
      case "vaapih264enc" =>
        s"videoconvert ! videoscale add-borders=true ! $scaleCaps ! " +
          s"vaapih264enc name=$EncoderName rate-control=cbr bitrate=$kbps keyframe-period=$gop max-bframes=0"
      case "nvh264enc" =>
        s"videoconvert ! videoscale add-borders=true ! $scaleCaps ! " +
          s"nvh264enc name=$EncoderName preset=p1 tune=ultra-low-latency zerolatency=true rc-mode=cbr bitrate=$kbps " +
          s"gop-size=$gop bframes=0 repeat-sequence-header=true min-force-key-unit-interval=500000000 ! " +
          "video/x-h264,profile=constrained-baseline"
      case _ =>
        s"videoconvert n-threads=4 ! videoscale ! $scaleCaps ! " +
          s"x264enc name=$EncoderName tune=zerolatency speed-preset=ultrafast bitrate=$kbps key-int-max=$gop " +
          "bframes=0 vbv-buf-capacity=500 threads=4 sliced-threads=true ! " +
          "video/x-h264,profile=constrained-baseline"
    }

    val rtpTail =
      "h264parse config-interval=-1 ! " +
        "rtph264pay pt=96 config-interval=-1 aggregate-mode=zero-latency mtu=1200 ! " +
        "application/x-rtp,media=video,encoding-name=H264,payload=96 ! " +
        s"$WebRTCBinName."

    s"$webrtcTail $head ! $encodeTail ! $rtpTail"
  }
}
