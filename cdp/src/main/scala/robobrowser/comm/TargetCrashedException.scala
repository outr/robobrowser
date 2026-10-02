package robobrowser.comm

/** The CDP target crashed or detached (`Inspector.targetCrashed`, `Target.targetCrashed`, `Inspector.detached`):
  * the [[SessionClosedException]] for [[SessionCloseCause.Crashed]] and [[SessionCloseCause.Detached]]. */
class TargetCrashedException(closed: SessionClosed)
  extends SessionClosedException(closed, s"CDP target lost (${closed.detail}) — the browser session is dead and must be recreated") {
  def this(reason: String) = this(SessionClosed(TargetCrashedException.causeOf(reason), reason))
}

object TargetCrashedException {
  private def causeOf(method: String): SessionCloseCause =
    if (method == "Inspector.detached") SessionCloseCause.Detached else SessionCloseCause.Crashed
}
