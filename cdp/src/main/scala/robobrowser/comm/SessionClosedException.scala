package robobrowser.comm

/** A CDP request on a closed session: every pending request fails with this when the session closes, and every
  * later `send` fails with it at once. The browser (or at least this tab's session) must be recreated. */
class SessionClosedException(val closed: SessionClosed, message: String) extends RuntimeException(message) {
  def this(closed: SessionClosed) =
    this(closed, s"CDP session closed (${closed.cause}: ${closed.detail}) — the browser session is gone and must be recreated")

  def reason: String = closed.detail
}
