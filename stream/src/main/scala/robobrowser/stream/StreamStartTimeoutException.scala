package robobrowser.stream

import scala.concurrent.duration.FiniteDuration

/** A stream's pipeline did not reach PLAYING within [[StreamConfig.startTimeout]]. The session is stopped. */
case class StreamStartTimeoutException(timeout: FiniteDuration)
  extends RuntimeException(s"Stream pipeline did not start within $timeout")
