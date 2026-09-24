package spec

import rapid._
import robobrowser.display.VirtualDisplayConfig
import robobrowser.stream.Stream.stream
import robobrowser.stream.PeerConnectionState
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._

/** Sessions started and stopped leave nothing behind: after a run of start/stop
  * cycles the session dispatcher threads and the GStreamer bus threads are back
  * at their baseline, the browser lists no session, and a session nobody
  * answered reads as never connected — the state a reaper acts on.
  * Run: sbt "stream/Test/runMain spec.StreamTeardownTest" */
object StreamTeardownTest extends RapidApp {
  private val Cycles = 6

  /** One run of start/stop cycles; the count of GStreamer bus threads afterwards. */
  private def batch(browser: RoboBrowser, label: String): Task[Int] =
    Task.sequence((1 to Cycles).toList.map { i =>
      browser.stream.start().flatMap { session =>
        Task.sleep(500.millis)
          .flatMap(_ => session.connectionState)
          .flatMap { state =>
            logger.info(s"$label cycle $i: unanswered session reads $state")
              .map(_ => require(!state.live && !state.over, s"an unanswered session should be new or connecting, not $state"))
          }
          .flatMap(_ => session.stop())
          .flatMap(_ => session.connectionState)
          .map(state => require(state == PeerConnectionState.Closed, s"a stopped session should read Closed, not $state"))
      }
    }).flatMap(_ => Task.sleep(2.seconds)).map(_ => threads("GstBus"))

  private def streamedConfig = RoboBrowserConfig(
    browserConfig = BrowserConfig(noSandbox = true, disableGPU = true),
    tabSelector = TabSelector.FirstPage,
    virtualDisplay = Some(VirtualDisplayConfig(width = 1280, height = 720))
  )

  /** The bus thread count once it is at or under `target`, or as it stands when `timeout` runs out. */
  private def settle(target: Int, timeout: FiniteDuration): Task[Int] = {
    val deadline = System.currentTimeMillis() + timeout.toMillis
    def loop: Task[Int] = Task.defer {
      val now = threads("GstBus")
      if (now <= target || System.currentTimeMillis() > deadline) Task.pure(now)
      else Task.sleep(1.second).flatMap(_ => loop)
    }
    loop
  }

  private def threads(prefix: String): Int =
    Thread.getAllStackTraces.keySet.asScala.count(_.getName.startsWith(prefix))

  override def run(args: List[String]): Task[Unit] =
    RoboBrowser.withBrowser(streamedConfig) { browser =>
      for {
        _ <- browser.navigate("data:text/html,<html><body style='background:%2300ff00'>teardown</body></html>")
        _ <- browser.waitForLoaded()
        sessionsBefore = threads("robobrowser-stream-session")
        busBefore = threads("GstBus")
        _ <- logger.info(s"baseline: session threads=$sessionsBefore bus threads=$busBefore")
        // GStreamer's task pool keeps the native threads that ran a pipeline
        // for reuse, so the first batch settles at a pool size; a second batch
        // of the same size must not grow it — a leak would.
        busAfterFirst <- batch(browser, "first")
        busAfterSecond <- batch(browser, "second")
        sessionsAfter = threads("robobrowser-stream-session")
        _ <- logger.info(s"after 2 x $Cycles cycles: session threads=$sessionsAfter bus threads=$busAfterFirst then $busAfterSecond, " +
          s"listed sessions=${browser.stream.sessions.size}")
        // The pool's idle threads die within GLib's idle timeout; a pipeline
        // still running keeps its threads alive past it.
        busSettled <- settle(busBefore + 1, 40.seconds)
        _ <- logger.info(s"bus threads once the pool drained: $busSettled")
        _ <- Task {
          require(browser.stream.sessions.isEmpty, s"stopped sessions still listed: ${browser.stream.sessions.size}")
          require(sessionsAfter == sessionsBefore, s"session dispatcher threads leaked: $sessionsBefore -> $sessionsAfter")
          require(busSettled <= busBefore + 1, s"GStreamer bus threads outlived their pipelines: $busBefore before, $busSettled after")
        }
        _ <- logger.info("StreamTeardownTest: PASS")
      } yield ()
    }
}
