package robobrowser.stream

import rapid._
import robobrowser.RoboBrowser
import robobrowser.stream.gst.GstEngine

/** WebRTC live streaming of a browser's virtual display — the high-throughput
  * alternative to the CDP screencast (see STREAMING.md). Requires the browser
  * to have been launched with `RoboBrowserConfig.virtualDisplay` and a working
  * GStreamer installation; check [[availability]] and fall back to
  * `browser.screencast` when it reports a reason (see [[StreamUnavailable]]).
  *
  * Reached as `browser.stream`:
  * {{{
  * import robobrowser.stream.*
  *
  * val session = browser.stream.start().sync()
  * session.toClient.attach(msg => sendOverWebSocket(msg.json))
  * // on websocket message: session.fromClient(json.as[SignalMessage])
  * }}}
  *
  * Each viewer gets its own session (one pipeline per viewer; `ximagesrc`
  * supports concurrent captures of one display). Sessions stop with the
  * browser via its disposal hooks. */
class Stream private(browser: RoboBrowser) {
  @volatile private var _sessions: List[StreamSession] = Nil

  /** None when streaming can proceed; Some(reason) when the caller should fall
    * back to `browser.screencast`. */
  def availability: Option[StreamUnavailable] = if (browser.virtualDisplay.isEmpty) {
    Some(StreamUnavailable.NoVirtualDisplay)
  } else {
    GstEngine.initResult match {
      case Left(error) => Some(StreamUnavailable.GStreamerMissing(error))
      case Right(_) =>
        val missing = GstEngine.missingElements
        if (missing.nonEmpty) {
          Some(StreamUnavailable.MissingPlugins(missing))
        } else if (GstEngine.selectedEncoder.isEmpty) {
          Some(StreamUnavailable.MissingPlugins(List("no usable H.264 encoder (tried " +
            s"${GstEngine.encoderPreference.mkString(", ")})")))
        } else {
          None
        }
    }
  }

  /** Active sessions (viewers) for this browser. */
  def sessions: List[StreamSession] = _sessions.filterNot(_.stopped())

  /** Start streaming to one viewer. `config.width`/`height` pick the size the
    * page renders and is captured at (any aspect); omitted, the whole display is
    * captured. Fails with [[StreamUnavailableException]] when [[availability]]
    * reports a reason. */
  def start(config: StreamConfig = StreamConfig()): Task[StreamSession] = availability match {
    case Some(reason) => Task.error(StreamUnavailableException(reason))
    case None =>
      val display = browser.virtualDisplay.get
      val encoder = config.encoderOverride.getOrElse(GstEngine.selectedEncoder.get)
      StreamSession.start(browser, display, config, encoder).map { session =>
        Stream.this.synchronized {
          _sessions = session :: _sessions
        }
        browser.onDispose(session.stop())
        session
      }
  }
}

object Stream {
  private val cache = new java.util.WeakHashMap[RoboBrowser, Stream]()

  def apply(browser: RoboBrowser): Stream = cache.synchronized {
    Option(cache.get(browser)).getOrElse {
      val stream = new Stream(browser)
      cache.put(browser, stream)
      stream
    }
  }

  extension (browser: RoboBrowser) {
    def stream: Stream = Stream(browser)
  }
}
