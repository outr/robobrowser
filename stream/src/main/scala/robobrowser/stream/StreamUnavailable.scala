package robobrowser.stream

/** Why WebRTC streaming can't run for a browser, reported by
  * `browser.stream.availability`. Callers should fall back to the CDP
  * screencast (`browser.screencast`), which has no native dependencies:
  *
  * {{{
  * browser.stream.availability match {
  *   case None         => browser.stream.start(config)
  *   case Some(reason) => scribe.warn(reason.message)
  *                        browser.screencast.start(onFrame)
  * }
  * }}} */
sealed trait StreamUnavailable {
  def message: String
}

object StreamUnavailable {
  /** The browser wasn't launched with `RoboBrowserConfig.virtualDisplay`, so
    * there is no dedicated display to capture. */
  case object NoVirtualDisplay extends StreamUnavailable {
    val message: String = "Browser was not launched with a virtual display (set RoboBrowserConfig.virtualDisplay)"
  }

  /** GStreamer native libraries failed to load or initialize. */
  case class GStreamerMissing(error: String) extends StreamUnavailable {
    def message: String = s"GStreamer unavailable: $error"
  }

  /** GStreamer loaded but required elements are missing (install the base,
    * good, bad and ugly plugin sets). */
  case class MissingPlugins(plugins: List[String]) extends StreamUnavailable {
    def message: String = s"Missing GStreamer plugins: ${plugins.mkString(", ")}"
  }
}

case class StreamUnavailableException(reason: StreamUnavailable) extends RuntimeException(reason.message)
