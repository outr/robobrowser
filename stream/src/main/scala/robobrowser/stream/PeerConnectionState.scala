package robobrowser.stream

import org.freedesktop.gstreamer.webrtc.WebRTCPeerConnectionState

/** Where a session's peer connection stands — webrtcbin's `connection-state`, read as this side's own type. */
enum PeerConnectionState {
  case New, Connecting, Connected, Disconnected, Failed, Closed

  /** Whether a viewer is on the other end right now. */
  def live: Boolean = this == Connected

  /** Whether the connection is over: the viewer went away, negotiation failed, or the session stopped. */
  def over: Boolean = this == Disconnected || this == Failed || this == Closed
}

object PeerConnectionState {
  def fromGst(state: WebRTCPeerConnectionState): PeerConnectionState = state match {
    case WebRTCPeerConnectionState.NEW          => New
    case WebRTCPeerConnectionState.CONNECTING   => Connecting
    case WebRTCPeerConnectionState.CONNECTED    => Connected
    case WebRTCPeerConnectionState.DISCONNECTED => Disconnected
    case WebRTCPeerConnectionState.FAILED       => Failed
    case WebRTCPeerConnectionState.CLOSED       => Closed
  }
}
