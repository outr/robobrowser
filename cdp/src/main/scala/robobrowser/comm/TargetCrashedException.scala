package robobrowser.comm

/** The CDP target crashed or detached (`Inspector.targetCrashed`,
  * `Target.targetCrashed`, `Inspector.detached`): every pending request is
  * failed with this, and every subsequent `send` throws it immediately.
  * The browser (or at least this tab's session) must be recreated —
  * without this, a request against the dead target waits forever. */
class TargetCrashedException(val reason: String)
  extends RuntimeException(s"CDP target lost ($reason) — the browser session is dead and must be recreated")
