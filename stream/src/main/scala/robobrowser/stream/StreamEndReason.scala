package robobrowser.stream

/** Why a [[StreamSession]] ended. Every reason takes the same teardown path and
  * ends with a `Bye` to the signaling listener. */
enum StreamEndReason {
  /** `stop()` was called: by the application, or by the browser's disposal. */
  case Stopped

  /** The viewer sent a `bye`. */
  case Bye

  /** The viewer went away without a `bye`: its peer connection failed or
    * closed, stayed disconnected for 10 seconds, or its
    * DataChannel closed. A closed tab, a reload or a lost network ends a
    * session this way; it is not an error. */
  case PeerGone
}
