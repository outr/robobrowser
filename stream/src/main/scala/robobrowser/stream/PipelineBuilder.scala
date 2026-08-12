package robobrowser.stream

/** Pure construction of the GStreamer launch description for a stream session
  * (kept side-effect free so it's testable without Gst.init). One pipeline per
  * session: display capture -> encode -> RTP -> webrtcbin.
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
  *  - A render target smaller than the display becomes an `ximagesrc` region
  *    anchored top-left, matching where CDP device-metrics emulation paints the
  *    emulated viewport. The captured rectangle is the target exactly, so a
  *    non-16:9 target never letterboxes. */
private[stream] object PipelineBuilder {
  val WebRTCBinName: String = "webrtc"

  /** Identity element after capture whose handoff signal feeds fps accounting
    * and the latency harness (a wallclock stamp per captured frame). */
  val TapName: String = "capture-tap"

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

  def description(displayName: String,
                  display: RenderSize,
                  config: StreamConfig,
                  encoder: String): String = {
    val target = targetSize(display, config)
    val encoded = encodeSize(target, config)
    val frameRate = fps(config)
    val kbps = math.max(1, config.maxBitrate / 1000)
    val gop = frameRate * 2
    val needsScale = encoded != target
    val width = encoded.width
    val height = encoded.height

    val webrtcTail = {
      val stun = config.stunServer.map(s => s" stun-server=$s").getOrElse("")
      val turn = config.turnServers.headOption.map(t => s" turn-server=$t").getOrElse("")
      s"webrtcbin name=$WebRTCBinName bundle-policy=max-bundle latency=40$stun$turn"
    }

    val region = if (target == display) {
      ""
    } else {
      s" startx=0 starty=0 endx=${target.width - 1} endy=${target.height - 1}"
    }

    val head =
      s"ximagesrc display-name=$displayName use-damage=true show-pointer=${config.showPointer}$region ! " +
        s"video/x-raw,framerate=$frameRate/1 ! queue max-size-buffers=2 leaky=downstream ! " +
        s"identity name=$TapName signal-handoffs=true silent=true"

    val encodeTail = encoder match {
      case "vah264enc" =>
        val caps = if (needsScale) s",width=$width,height=$height" else ""
        // vapostproc scales on the GPU, so no videoscale on this branch
        "videoconvert ! vapostproc ! " +
          s"video/x-raw(memory:VAMemory),format=NV12$caps ! " +
          s"vah264enc rate-control=cbr bitrate=$kbps cpb-size=$kbps key-int-max=$gop " +
          "b-frames=0 ref-frames=1 target-usage=6 min-force-key-unit-interval=500000000 ! " +
          // Without a profile pin the encoder may offer a profile the browser
          // rejects, killing the whole bundle (the video m-line carries ICE)
          "video/x-h264,profile=constrained-baseline"
      case "vaapih264enc" =>
        val scale = if (needsScale) s"videoscale ! video/x-raw,width=$width,height=$height ! " else ""
        s"videoconvert ! ${scale}vaapih264enc rate-control=cbr bitrate=$kbps keyframe-period=$gop max-bframes=0"
      case "nvh264enc" =>
        val caps = if (needsScale) s",width=$width,height=$height" else ""
        "videoconvert ! videoscale ! " +
          s"video/x-raw,format=NV12$caps ! " +
          s"nvh264enc preset=p1 tune=ultra-low-latency zerolatency=true rc-mode=cbr bitrate=$kbps " +
          s"gop-size=$gop bframes=0 repeat-sequence-header=true min-force-key-unit-interval=500000000 ! " +
          "video/x-h264,profile=constrained-baseline"
      case _ =>
        val caps = if (needsScale) s",width=$width,height=$height" else ""
        "videoconvert n-threads=4 ! videoscale ! " +
          s"video/x-raw,format=I420$caps ! " +
          s"x264enc tune=zerolatency speed-preset=ultrafast bitrate=$kbps key-int-max=$gop " +
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
