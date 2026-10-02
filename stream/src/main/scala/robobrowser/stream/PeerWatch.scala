package robobrowser.stream

import rapid._

import scala.concurrent.duration.FiniteDuration

/** Follows one viewer's transport — the peer connection's state and the input
  * DataChannel's — and decides when that viewer has gone without a `bye`.
  *
  * The viewer is gone once its peer connection reads failed or closed, once it
  * has read disconnected for `grace` without recovering, or once its DataChannel
  * closes after having opened (nothing reopens it: the session never
  * renegotiates). `gone` is called once, with the reason, on whichever thread
  * observed it. */
private[stream] final class PeerWatch(grace: FiniteDuration, gone: String => Unit) {
  private var state: PeerConnectionState = PeerConnectionState.New
  private var generation: Long = 0L
  private var opened: Boolean = false
  private var open: Boolean = false
  private var left: Boolean = false

  /** The peer connection's latest state. */
  def peer(next: PeerConnectionState): Unit = {
    val departure = synchronized {
      if (next == state) None
      else {
        state = next
        generation += 1
        next match {
          case PeerConnectionState.Failed | PeerConnectionState.Closed => leave(s"peer connection ${next.toString.toLowerCase}")
          case PeerConnectionState.Disconnected =>
            val expected = generation
            Task.sleep(grace).map(_ => expire(expected)).start()
            None
          case _ => None
        }
      }
    }
    departure.foreach(gone)
  }

  def channelOpened(): Unit = synchronized {
    opened = true
    open = true
  }

  def channelClosed(): Unit = {
    val departure = synchronized {
      if (open) {
        open = false
        leave("data channel closed")
      } else None
    }
    departure.foreach(gone)
  }

  /** Whether a write to the DataChannel can reach the viewer. */
  def writable: Boolean = synchronized(open && !state.over && !left)

  /** Whether the viewer has been found gone. */
  def departed: Boolean = synchronized(left)

  /** A failure is the viewer's departure when the transport was already down as
    * it arrived: the viewer found gone, the peer connection disconnected, failed
    * or closed, or the DataChannel closed after opening. Anything else, including
    * a failure before the viewer ever connected, is a fault. */
  def classify: PipelineFailure = synchronized {
    if (left || state.over || (opened && !open)) PipelineFailure.PeerGone else PipelineFailure.Fault
  }

  /** Mark the viewer gone after a failure classified as [[PipelineFailure.PeerGone]]. */
  def failed(reason: String): Unit = synchronized(leave(reason)).foreach(gone)

  private def expire(expected: Long): Unit = {
    val departure = synchronized {
      if (generation == expected && state == PeerConnectionState.Disconnected) {
        leave(s"peer connection disconnected for $grace")
      } else None
    }
    departure.foreach(gone)
  }

  private def leave(reason: String): Option[String] = if (left) None else {
    left = true
    Some(reason)
  }
}
