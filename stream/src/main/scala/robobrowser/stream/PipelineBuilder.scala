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
  *    of it by a named `videocrop` anchored top-left, matching where CDP
  *    device-metrics emulation paints the emulated viewport. The cropped
  *    rectangle is the target exactly, so a non-16:9 target never letterboxes.
  *  - A named capsfilter always pins the resolution the encoder receives, even
  *    when it already matches the crop. Both the crop insets and those caps are
  *    settable while the pipeline plays, which is what lets
  *    [[StreamSession.resize]] reconfigure in place: webrtcbin, its DTLS
  *    session, its ICE credentials and the input DataChannel are never
  *    disturbed, and no second offer is emitted. */
private[stream] object PipelineBuilder {
  val WebRTCBinName: String = "webrtc"

  /** Identity element after capture whose handoff signal feeds fps accounting
    * and the latency harness (a wallclock stamp per captured frame). */
  val TapName: String = "capture-tap"

  /** Crops the full-display capture down to the render target. */
  val CropName: String = "capture-crop"

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

  /** The caps [[ScaleCapsName]] pins for `size`, in the memory space and pixel
    * format its encoder branch consumes. A resize swaps the property to exactly
    * this string, so the running pipeline and a freshly built one agree. */
  def encodeCaps(encoder: String, size: RenderSize): String = {
    val dimensions = s"width=${size.width},height=${size.height}"
    encoder match {
      case "vah264enc" => s"video/x-raw(memory:VAMemory),format=NV12,$dimensions"
      case "vaapih264enc" => s"video/x-raw,$dimensions"
      case "nvh264enc" => s"video/x-raw,format=NV12,$dimensions"
      case _ => s"video/x-raw,format=I420,$dimensions"
    }
  }

  def description(displayName: String,
                  display: RenderSize,
                  config: StreamConfig,
                  encoder: String): String = {
    val target = targetSize(display, config)
    val encoded = encodeSize(target, config)
    val frameRate = fps(config)
    val kbps = math.max(1, config.maxBitrate / 1000)
    val gop = frameRate * 2

    val webrtcTail = {
      val stun = config.stunServer.map(s => s" stun-server=$s").getOrElse("")
      val turn = config.turnServers.headOption.map(t => s" turn-server=$t").getOrElse("")
      s"webrtcbin name=$WebRTCBinName bundle-policy=max-bundle latency=40$stun$turn"
    }

    val head =
      s"ximagesrc display-name=$displayName use-damage=true show-pointer=${config.showPointer} ! " +
        s"video/x-raw,framerate=$frameRate/1 ! queue max-size-buffers=2 leaky=downstream ! " +
        s"identity name=$TapName signal-handoffs=true silent=true ! " +
        s"videocrop name=$CropName ${CropRegion(display, target).launchArgs}"

    val scaleCaps = s"capsfilter name=$ScaleCapsName caps=${encodeCaps(encoder, encoded)}"

    val encodeTail = encoder match {
      case "vah264enc" =>
        // vapostproc scales on the GPU, so no videoscale on this branch
        s"videoconvert ! vapostproc ! $scaleCaps ! " +
          s"vah264enc name=$EncoderName rate-control=cbr bitrate=$kbps cpb-size=$kbps key-int-max=$gop " +
          "b-frames=0 ref-frames=1 target-usage=6 min-force-key-unit-interval=500000000 ! " +
          // Without a profile pin the encoder may offer a profile the browser
          // rejects, killing the whole bundle (the video m-line carries ICE)
          "video/x-h264,profile=constrained-baseline"
      case "vaapih264enc" =>
        s"videoconvert ! videoscale ! $scaleCaps ! " +
          s"vaapih264enc name=$EncoderName rate-control=cbr bitrate=$kbps keyframe-period=$gop max-bframes=0"
      case "nvh264enc" =>
        s"videoconvert ! videoscale ! $scaleCaps ! " +
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
