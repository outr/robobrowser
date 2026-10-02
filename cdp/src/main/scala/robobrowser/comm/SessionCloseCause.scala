package robobrowser.comm

/** Why a CDP session closed. */
enum SessionCloseCause {
  /** The target crashed (`Inspector.targetCrashed`, `Target.targetCrashed`). */
  case Crashed
  /** The target detached (`Inspector.detached`). */
  case Detached
  /** The browser was disposed by its owner. */
  case Disposed
  /** The DevTools WebSocket closed or errored, e.g. the browser process died. */
  case SocketClosed
}
