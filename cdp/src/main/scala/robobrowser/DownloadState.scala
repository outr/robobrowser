package robobrowser

import spice.net.URL

import java.time.Instant
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._

object DownloadState {
  case class DownloadTriggeredException(url: URL)
    extends RuntimeException(s"Download triggered for $url")

  private case class FrameTrigger(url: URL, at: Instant)

  private val triggeredByUrl = TrieMap.empty[String, Instant]
  private val triggeredByHost = TrieMap.empty[String, Instant]
  private val triggeredByFrame = TrieMap.empty[String, FrameTrigger]
  private val defaultWindow: FiniteDuration = 30.seconds

  def mark(url: URL,
           frameId: Option[String] = None,
           at: Instant = Instant.now()): Unit = {
    cleanup()
    triggeredByUrl.put(url.toString(), at)
    triggeredByHost.put(url.host, at)
    frameId.foreach(id => triggeredByFrame.put(id, FrameTrigger(url, at)))
  }

  def wasRecentlyTriggered(url: URL,
                           within: FiniteDuration = defaultWindow,
                           includeHost: Boolean = true): Boolean = {
    cleanup(within * 2)
    val now = Instant.now()
    val direct = triggeredByUrl
      .get(url.toString())
      .exists(_.isAfter(now.minusNanos(within.toNanos)))
    lazy val hostMatch = includeHost && triggeredByHost
      .get(url.host)
      .exists(_.isAfter(now.minusNanos(within.toNanos)))
    direct || hostMatch
  }

  def consumeFrameTrigger(frameId: String,
                          within: FiniteDuration = defaultWindow): Option[URL] = {
    cleanup(within * 2)
    val now = Instant.now()
    triggeredByFrame.remove(frameId).collect {
      case FrameTrigger(url, at) if at.isAfter(now.minusNanos(within.toNanos)) => url
    }
  }

  private def cleanup(retention: FiniteDuration = defaultWindow * 2): Unit = {
    val cutoff = Instant.now().minusNanos(retention.toNanos)
    triggeredByUrl.foreach { case (key, ts) =>
      if (ts.isBefore(cutoff)) {
        triggeredByUrl.remove(key, ts)
      }
    }
    triggeredByHost.foreach { case (key, ts) =>
      if (ts.isBefore(cutoff)) {
        triggeredByHost.remove(key, ts)
      }
    }
    triggeredByFrame.foreach { case (key, trigger) =>
      if (trigger.at.isBefore(cutoff)) {
        triggeredByFrame.remove(key, trigger)
      }
    }
  }
}

