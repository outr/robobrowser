package robobrowser.stream

/** How a pipeline or DataChannel failure reads against the viewer's transport
  * at the moment it arrived. */
private[stream] enum PipelineFailure {
  /** The viewer's transport was already down: the write had nowhere to go. */
  case PeerGone

  /** The transport was still up: a real fault. */
  case Fault
}
