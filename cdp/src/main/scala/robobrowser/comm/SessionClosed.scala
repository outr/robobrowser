package robobrowser.comm

/** A closed CDP session: the `cause`, and `detail` naming what closed it (the CDP event, the socket error, ...).
  * No pending or later request on the session can be answered. */
case class SessionClosed(cause: SessionCloseCause, detail: String) {
  /** The exception a request on the closed session fails with: a [[TargetCrashedException]] for a crash or detach,
    * otherwise a [[SessionClosedException]]. */
  def exception: SessionClosedException = cause match {
    case SessionCloseCause.Crashed | SessionCloseCause.Detached => new TargetCrashedException(this)
    case _ => new SessionClosedException(this)
  }
}
