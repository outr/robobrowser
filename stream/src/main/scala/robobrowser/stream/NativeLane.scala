package robobrowser.stream

import rapid.Task

import java.util.concurrent.{ExecutorService, Executors, TimeoutException}
import scala.concurrent.duration.FiniteDuration

/** A dedicated daemon thread for native calls that can block indefinitely. Work runs in submission order. A caller
  * waits a bounded time; a call that outlives the wait keeps the lane's thread busy, never the caller's. */
private[stream] final class NativeLane(val name: String) {
  private val executor: ExecutorService = Executors.newSingleThreadExecutor { r =>
    val thread = new Thread(r, name)
    thread.setDaemon(true)
    thread
  }

  /** Run `f` on the lane and wait at most `timeout`: `Some` with its result, or `None` while it is still running. A
    * failure of `f` fails the task. */
  def run[T](timeout: FiniteDuration)(f: => T): Task[Option[T]] = Task.defer {
    val done = Task.completable[T]
    executor.execute { () =>
      try done.success(f)
      catch {
        case t: Throwable => done.failure(t)
      }
    }
    done.timeout(timeout).map(Some(_): Option[T]).handleError {
      case _: TimeoutException if done.result.isEmpty => Task.pure(None)
      case t => Task.error(t)
    }
  }

  /** Accept no more work; queued and running work still finishes. */
  def shutdown(): Unit = executor.shutdown()
}
