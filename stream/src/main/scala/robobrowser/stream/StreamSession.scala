package robobrowser.stream

import com.sun.jna.Pointer
import fabric.Json
import fabric.io.JsonParser
import fabric.rw._
import org.freedesktop.gstreamer.event.EventType
import org.freedesktop.gstreamer.lowlevel.GstAPI.GstCallback
import org.freedesktop.gstreamer.lowlevel.{GObjectAPI, GstEventAPI, GstStructureAPI}
import org.freedesktop.gstreamer.webrtc.{WebRTCBin, WebRTCSDPType, WebRTCSessionDescription}
import org.freedesktop.gstreamer.{Bus, Caps, Element, Gst, GstObject, Pipeline, Promise, SDPMessage, State, Structure}
import rapid._
import reactify.{Channel, Val, Var}
import robobrowser.RoboBrowser
import robobrowser.display.VirtualDisplay
import robobrowser.stream.gst.WebRTCDataChannel

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.duration.{DurationDouble, DurationLong, FiniteDuration}
import scala.util.Try

/** One WebRTC stream of a browser's virtual display to one viewer.
  *
  * Signaling: attach to [[toClient]] and forward each message to your viewer
  * over any transport; feed the viewer's messages into [[fromClient]]. The
  * server offers; the viewer answers; ICE trickles both ways.
  *
  * Threading: all webrtcbin interaction and all [[toClient]] fires happen on a
  * single-thread dispatcher, never on GStreamer/libnice internal threads —
  * consumers may block briefly in a `toClient` listener without stalling the
  * media pipeline (reactify fires listeners synchronously on the firing
  * thread). */
class StreamSession private(browser: RoboBrowser,
                            display: VirtualDisplay,
                            val config: StreamConfig,
                            encoder: String,
                            initialTarget: RenderSize) {
  private val dispatcher = Executors.newSingleThreadExecutor(r => {
    val t = new Thread(r, "robobrowser-stream-session")
    t.setDaemon(true)
    t
  })

  val toClient: Channel[SignalMessage] = Channel[SignalMessage]
  // The offer fires from webrtcbin moments after PLAYING — often before the
  // consumer has attached to toClient. Messages emitted before connect() are
  // buffered (dispatcher-confined) and flushed on connect.
  private var connected: Boolean = false
  private var pending: List[SignalMessage] = Nil
  private val _stopped: Var[Boolean] = Var(false)
  def stopped: Val[Boolean] = _stopped

  // Render geometry follows `resize`, which reconfigures the running pipeline's
  // crop and encoder caps rather than rebuilding it.
  @volatile private var target: RenderSize = initialTarget
  @volatile private var encoded: RenderSize = PipelineBuilder.encodeSize(initialTarget, config)
  @volatile private var router: InputRouter = new InputRouter(browser, target, encoded, deviceScaleFactor = 1.0)

  /** The rectangle of the display this session renders and captures. */
  def renderSize: RenderSize = target

  private val stopping = new AtomicBoolean(false)
  private val negotiated = new AtomicBoolean(false)

  // Frame/latency accounting (fed by the identity tap and viewer reports)
  private val frameCount = new AtomicLong(0L)
  private val lastStatsFrames = new AtomicLong(0L)
  private val lastStatsBytes = new AtomicLong(0L)
  private val lastStatsTime = new AtomicLong(System.currentTimeMillis())
  @volatile private var reportedLatency: Option[FiniteDuration] = None

  private var pipeline: Pipeline = scala.compiletime.uninitialized
  private var webrtc: WebRTCBin = scala.compiletime.uninitialized
  private var channel: Option[WebRTCDataChannel] = None

  // Listener references are session fields deliberately: the binding keys its
  // JNA callback retention on these, and a collected callback is a native crash.
  private val busError = new Bus.ERROR {
    override def errorMessage(source: GstObject, code: Int, message: String): Unit = {
      scribe.error(s"Stream pipeline error from ${source.getName}: $message")
      onDispatcher {
        emit(SignalMessage.Error(message))
      }
    }
  }
  private val onIce = new WebRTCBin.ON_ICE_CANDIDATE {
    override def onIceCandidate(sdpMLineIndex: Int, candidate: String): Unit = onDispatcher {
      emit(SignalMessage.Ice(sdpMLineIndex, candidate))
    }
  }
  private val onNegotiation = new WebRTCBin.ON_NEGOTIATION_NEEDED {
    override def onNegotiationNeeded(elem: Element): Unit = {
      if (negotiated.compareAndSet(false, true)) {
        webrtc.createOffer(offerCreated)
      } else {
        // The topology is fixed for the session's lifetime — even a resize only
        // reconfigures elements — so there is nothing left to negotiate.
        scribe.warn("Ignoring repeat on-negotiation-needed for an unchanged pipeline")
      }
    }
  }
  private val offerCreated = new WebRTCBin.CREATE_OFFER {
    override def onOfferCreated(offer: WebRTCSessionDescription): Unit = {
      webrtc.setLocalDescription(offer)
      val sdp = offer.getSDPMessage.toString
      onDispatcher {
        emit(SignalMessage.Offer(sdp))
      }
    }
  }
  private val onChannelMessage = new WebRTCDataChannel.OnMessageString {
    override def onMessage(message: String): Unit = onDispatcher(handleChannelMessage(message))
  }
  // Latency harness: throttled wallclock capture stamps over the DataChannel;
  // the viewer diffs them against requestVideoFrameCallback arrival (with the
  // clock offset estimated via ping/pong) and reports back a "latency" message.
  private val lastFrameStamp = new AtomicLong(0L)
  private val tapHandoff = new GstCallback {
    def callback(identity: Pointer, buffer: Pointer, userData: Pointer): Unit = {
      frameCount.incrementAndGet()
      val now = System.currentTimeMillis()
      val last = lastFrameStamp.get()
      if (now - last >= 1000L && lastFrameStamp.compareAndSet(last, now)) {
        onDispatcher {
          channel.foreach(_.sendString(s"""{"type": "frame", "t": $now}"""))
        }
      }
    }
  }
  private object tapListener

  private def onDispatcher(f: => Unit): Unit = dispatcher.execute { () =>
    try {
      f
    } catch {
      case t: Throwable => scribe.error(s"Stream session dispatch failure: ${t.getMessage}")
    }
  }

  private def emit(message: SignalMessage): Unit = if (connected) {
    toClient @= message
  } else {
    pending = pending :+ message
  }

  /** Attach a signaling listener and flush any messages emitted before the
    * consumer was ready (the offer typically fires while `start` is still
    * returning). Use this rather than attaching to [[toClient]] directly. */
  def connect(listener: SignalMessage => Unit): Task[Unit] = dispatcherTask {
    toClient.attach(listener)
    connected = true
    pending.foreach(msg => toClient @= msg)
    pending = Nil
  }

  /** A lazy Task that runs `f` on the session dispatcher when executed. The
    * Task.defer is load-bearing: without it the work would be submitted the
    * moment the method is *called* (e.g. registering `session.stop()` as a
    * dispose hook would tear the session down immediately). */
  private def dispatcherTask[T](f: => T): Task[T] = Task.defer {
    val completable = Task.completable[T]
    dispatcher.execute { () =>
      try {
        completable.success(f)
      } catch {
        case t: Throwable => completable.failure(t)
      }
    }
    completable
  }

  private def start(description: String): Unit = {
    scribe.info(s"Stream pipeline: $description")
    pipeline = Gst.parseLaunch(description).asInstanceOf[Pipeline]
    webrtc = pipeline.getElementByName(PipelineBuilder.WebRTCBinName).asInstanceOf[WebRTCBin]
    pipeline.getBus.connect(busError)
    webrtc.connect(onIce)
    webrtc.connect(onNegotiation)
    Option(pipeline.getElementByName(PipelineBuilder.TapName)).foreach { tap =>
      tap.connect("handoff", classOf[AnyRef], tapListener, tapHandoff)
    }
    // The DataChannel must exist before PLAYING triggers negotiation so it's
    // part of the initial SDP — the fixed topology is what avoids renegotiation.
    pipeline.setState(State.READY)
    val dc = webrtc.emit(classOf[WebRTCDataChannel], "create-data-channel", "input", null)
    if (dc == null) {
      throw new RuntimeException("webrtcbin create-data-channel returned null")
    }
    dc.onMessageString(onChannelMessage)
    channel = Some(dc)
    pipeline.setState(State.PLAYING)
  }

  /** Feed a message from the viewer (answer / ice / bye). */
  def fromClient(message: SignalMessage): Task[Unit] = dispatcherTask {
    message match {
      case SignalMessage.Answer(sdpText) =>
        val sdp = new SDPMessage()
        sdp.parseBuffer(sdpText)
        webrtc.setRemoteDescription(new WebRTCSessionDescription(WebRTCSDPType.ANSWER, sdp))
        scribe.debug("Stream: client answer applied")
      case SignalMessage.Ice(index, candidate) =>
        webrtc.addIceCandidate(index, candidate)
        scribe.debug(s"Stream: client ICE candidate added (mline $index)")
      case SignalMessage.Bye =>
        stop().start()
      case SignalMessage.Error(error) =>
        scribe.warn(s"Stream client error: $error")
      case SignalMessage.Offer(_) =>
        scribe.warn("Ignoring client offer: the server is always the offerer")
    }
  }

  /** DataChannel traffic: input events (routed to CDP dispatch when enabled)
    * plus the measurement control messages (ping echo, latency reports). */
  private def handleChannelMessage(raw: String): Unit = Try(JsonParser(raw)).toOption match {
    case Some(json) => json.get("type").map(_.asString) match {
      case Some("ping") =>
        val t = json.get("t").map(_.asLong).getOrElse(0L)
        channel.foreach(_.sendString(s"""{"type": "pong", "t": $t, "serverT": ${System.currentTimeMillis()}}"""))
      case Some("latency") =>
        reportedLatency = json.get("value").map(_.asDouble.millis)
      case Some(_) if config.routeInput =>
        Try(router.dispatch(json.as[InputMessage]).start())
          .failed.foreach(t => scribe.warn(s"Ignoring input message: ${t.getMessage}"))
      case _ => // input routing disabled or untyped message
    }
    case None => scribe.warn("Ignoring unparseable DataChannel message")
  }

  /** Point-in-time stats; fps/bitrate are rates since the previous call. */
  def stats: Task[StreamStats] = dispatcherTask {
    val now = System.currentTimeMillis()
    val frames = frameCount.get()
    val elapsedMs = math.max(1L, now - lastStatsTime.getAndSet(now))
    val fps = (frames - lastStatsFrames.getAndSet(frames)) * 1000.0 / elapsedMs

    val (rtt, packetsLost, bytesSent) = webrtcStats()
    val bitrate = bytesSent.map { bytes =>
      (bytes - lastStatsBytes.getAndSet(bytes)) * 8000L / elapsedMs
    }.getOrElse(0L)

    StreamStats(
      encoder = encoder,
      codec = config.codec,
      width = encoded.width,
      height = encoded.height,
      fps = fps,
      bitrate = math.max(0L, bitrate),
      rtt = rtt,
      packetsLost = packetsLost,
      latency = reportedLatency
    )
  }

  /** Query webrtcbin's get-stats and extract (rtt, packetsLost, bytesSent);
    * best-effort — missing fields simply stay empty. */
  private def webrtcStats(): (Option[FiniteDuration], Long, Option[Long]) = Try {
    val promise = new Promise()
    webrtc.emit("get-stats", null, promise)
    promise.waitResult()
    val reply = promise.getReply
    var rtt: Option[FiniteDuration] = None
    var lost = 0L
    var sent: Option[Long] = None
    if (reply != null) {
      // Numeric fields vary in GType (packets-lost is gint64, bytes-sent
      // guint64), so everything goes through getValue + toString; each field
      // is guarded independently so one marshalling failure can't blank the rest
      def long(s: Structure, field: String): Option[Long] =
        if (s.hasField(field)) Try(s.getValue(field).toString.toDouble.toLong).toOption else None
      def double(s: Structure, field: String): Option[Double] =
        if (s.hasField(field)) Try(s.getValue(field).toString.toDouble).toOption else None
      (0 until reply.getFields).foreach { i =>
        Try(reply.getValue(reply.getName(i))).toOption.collect {
          case s: Structure => s
        }.foreach { s =>
          if (s.getName == "remote-inbound-rtp") {
            double(s, "round-trip-time").foreach(seconds => rtt = Some(seconds.seconds))
            long(s, "packets-lost").foreach(lost += _)
          }
          if (s.getName == "outbound-rtp") {
            long(s, "bytes-sent").foreach(bytes => sent = Some(bytes))
          }
        }
      }
      promise.interrupt()
    }
    (rtt, lost, sent)
  }.getOrElse((None, 0L, None))

  /**
   * Change the size the page is rendered and captured at, mid-session.
   *
   * The page re-lays-out at the new size (CDP device-metrics emulation, cleared
   * when the target is the whole display again) and the running pipeline follows
   * it: the capture crop and the caps pinning the encoder's input are swapped
   * while it plays. Any aspect ratio is honoured verbatim — a portrait target
   * produces portrait video, not a letterboxed landscape frame.
   *
   * Nothing renegotiates. `webrtcbin`, its DTLS session, its ICE credentials and
   * the input DataChannel are untouched, so no second offer is emitted and the
   * viewer keeps decoding the track it already has. H.264 carries resolution
   * in-band — SPS/PPS ride every IDR, re-injected by `h264parse
   * config-interval=-1` — and the RTP caps are resolution-independent, so the
   * SDP has nothing new to say.
   *
   * A target larger than the virtual display is attempted as a display resize
   * first and raises
   * [[robobrowser.display.DisplayResizeUnsupportedException]] when the X server
   * refuses.
   */
  def resize(width: Int, height: Int): Task[Unit] = Task.defer {
    require(width > 0 && height > 0, s"Render size must be positive, got ${width}x$height")
    val requested = RenderSize.even(width, height)
    if (stopping.get()) {
      Task.error(new IllegalStateException("Cannot resize a stopped stream session"))
    } else if (requested == target) {
      Task.unit
    } else {
      StreamSession.fitDisplay(display, requested)
        .flatMap(_ => StreamSession.applyRenderTarget(browser, display, requested))
        .flatMap(_ => dispatcherTask(reconfigure(requested)))
    }
  }

  /** Dispatcher-confined reconfiguration of the playing pipeline: crop the new
    * target out of the display capture, re-pin what the encoder receives, then
    * force an IDR so the viewer repaints at the new resolution immediately
    * rather than at the next GOP boundary. */
  private def reconfigure(requested: RenderSize): Unit = {
    val region = CropRegion(StreamSession.displaySize(display), requested)
    val requestedEncoded = PipelineBuilder.encodeSize(requested, config)
    Option(pipeline.getElementByName(PipelineBuilder.CropName)).foreach { crop =>
      crop.set("left", region.left)
      crop.set("top", region.top)
      crop.set("right", region.right)
      crop.set("bottom", region.bottom)
    }
    Option(pipeline.getElementByName(PipelineBuilder.ScaleCapsName)).foreach { filter =>
      // capsfilter's `caps` is a boxed GstCaps, which GObject.set doesn't
      // marshal; g_object_set takes the pointer directly
      GObjectAPI.GOBJECT_API.g_object_set(filter, "caps",
        Caps.fromString(PipelineBuilder.encodeCaps(encoder, requestedEncoded)), null)
    }
    forceKeyframe()
    target = requested
    encoded = requestedEncoded
    router = new InputRouter(browser, target, encoded, deviceScaleFactor = 1.0)
    scribe.info(s"Stream reconfigured to $requested (encoded $encoded, crop ${region.launchArgs})")
  }

  /** The same upstream force-key-unit event webrtcbin's rtpbin synthesizes from
    * a client PLI, sent straight at the encoder's source pad. `all-headers`
    * makes the IDR carry a fresh SPS/PPS describing the new resolution. */
  private def forceKeyframe(): Unit = Option(pipeline.getElementByName(PipelineBuilder.EncoderName))
    .flatMap(element => Option(element.getStaticPad("src")))
    .foreach { pad =>
      val structure = GstStructureAPI.GSTSTRUCTURE_API
        .gst_structure_from_string("GstForceKeyUnit, all-headers=(boolean)true", null)
      pad.sendEvent(GstEventAPI.GSTEVENT_API.gst_event_new_custom(EventType.CUSTOM_UPSTREAM, structure))
    }

  /** Idempotent teardown of this session's pipeline. The Xvfb display belongs
    * to the browser and is disposed by `browser.dispose()`, not here. */
  def stop(): Task[Unit] = Task.defer {
    if (stopping.compareAndSet(false, true)) {
      doStop()
    } else {
      Task.unit
    }
  }

  private def doStop(): Task[Unit] = {
    dispatcherTask {
      Try(emit(SignalMessage.Bye))
      teardownPipeline()
      _stopped @= true
    }.guarantee(Task(dispatcher.shutdown()))
  }

  /** Dispatcher-confined native teardown. Blocks until the state change
    * completes so the display capture and encoder are genuinely released before
    * the session object is discarded. */
  private def teardownPipeline(): Unit = {
    channel.foreach { dc =>
      Try(dc.closeChannel())
      Try(dc.dispose())
    }
    channel = None
    pipeline.setState(State.NULL)
    pipeline.getState(5, TimeUnit.SECONDS)
    pipeline.dispose()
  }
}

private[stream] object StreamSession {
  def start(browser: RoboBrowser,
            display: VirtualDisplay,
            config: StreamConfig,
            encoder: String): Task[StreamSession] = {
    val target = PipelineBuilder.targetSize(displaySize(display), config)
    fitDisplay(display, target)
      .flatMap(_ => applyRenderTarget(browser, display, target))
      .map { _ =>
        val session = new StreamSession(browser, display, config, encoder, target)
        session.start(describe(display, config, encoder))
        session
      }
  }

  private[stream] def displaySize(display: VirtualDisplay): RenderSize =
    RenderSize(display.width, display.height)

  private def describe(display: VirtualDisplay, config: StreamConfig, encoder: String): String =
    PipelineBuilder.description(display.displayName, displaySize(display), config, encoder)

  /** Grow the display when the target doesn't fit inside it. Shrinking is never
    * a display concern — a smaller target is a capture region. */
  private def fitDisplay(display: VirtualDisplay, target: RenderSize): Task[Unit] =
    if (target.fitsWithin(displaySize(display))) {
      Task.unit
    } else {
      display.resize(math.max(target.width, display.width), math.max(target.height, display.height))
    }

  /**
   * Make the page lay out at the render target. A target that is the whole
   * display clears the override so the kiosk window's native size is what the
   * page sees; anything smaller is emulated, which paints the viewport at
   * exactly that size anchored to the display's top-left — where the capture
   * region reads it from.
   */
  private def applyRenderTarget(browser: RoboBrowser, display: VirtualDisplay, target: RenderSize): Task[Unit] =
    if (target == displaySize(display)) {
      browser.clearViewportOverride()
    } else {
      browser.setViewportSize(target.width, target.height)
    }
}
