package spec

import fabric.{Bool, Str, obj}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import rapid.{AsyncTaskSpec, Task}
import robobrowser.comm.{SessionCloseCause, SessionClosedException, WSResponse}
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig}

import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*
import scala.util.Try

/** A request on a browser whose session has closed (disposed, or its process killed) fails with the typed reason
  * instead of waiting for an answer that never comes. */
class SessionCloseSpec extends AsyncWordSpec with AsyncTaskSpec with Matchers {
  private def launch(): Task[RoboBrowser] =
    RoboBrowser(RoboBrowserConfig(browserConfig = BrowserConfig(noSandbox = true, disableDevSHMUsage = true, disableGPU = true)))

  /** A request Chrome holds unanswered until the session ends: a promise that never settles, awaited. */
  private def pending(browser: RoboBrowser): Task[WSResponse] =
    browser.send("Runtime.evaluate", obj("expression" -> Str("new Promise(() => {})"), "awaitPromise" -> Bool(true)))

  /** Start `request` in the background; the returned call reads its outcome once it has one. */
  private def inFlight(request: Task[WSResponse]): () => Option[Try[WSResponse]] = {
    @volatile var outcome: Option[Try[WSResponse]] = None
    request.attempt.map(r => outcome = Some(r)).start(): Unit
    () => outcome
  }

  private def closedCause(result: Try[?]): SessionCloseCause = result.failed.get match {
    case e: SessionClosedException => e.closed.cause
    case other => fail(s"expected a SessionClosedException, got $other")
  }

  "A RoboBrowser's session" should {
    "fail a send after dispose at once" in {
      for {
        browser <- launch()
        _ <- browser.navigate("about:blank")
        _ <- browser.send("Runtime.evaluate", obj("expression" -> Str("1+1")))
        _ <- browser.dispose()
        started = System.currentTimeMillis()
        result <- browser.send("Page.reload", obj("ignoreCache" -> Bool(true))).timeout(10.seconds).attempt
      } yield {
        closedCause(result) shouldBe SessionCloseCause.Disposed
        (System.currentTimeMillis() - started) should be < 1000L
        browser.closed.map(_.cause) shouldBe Some(SessionCloseCause.Disposed)
        browser.crashed shouldBe None
        browser.attached() shouldBe false
      }
    }
    "fail a send in flight when dispose is called" in {
      for {
        browser <- launch()
        outcome = inFlight(pending(browser).timeout(10.seconds))
        _ <- Task.sleep(1.second)
        stillPending = outcome().isEmpty
        _ <- browser.dispose()
        result <- waitFor(outcome)
      } yield {
        stillPending shouldBe true
        closedCause(result) shouldBe SessionCloseCause.Disposed
      }
    }
    "fail pending and later sends when the browser process is killed" in {
      val before = children()
      for {
        browser <- launch()
        launched = children() -- before
        outcome = inFlight(pending(browser).timeout(10.seconds))
        _ <- Task.sleep(1.second)
        stillPending = outcome().isEmpty
        _ <- Task(launched.foreach(_.destroyForcibly()))
        result <- waitFor(outcome)
        later <- browser.send("Runtime.evaluate", obj("expression" -> Str("1+1"))).timeout(10.seconds).attempt
        _ <- browser.dispose()
      } yield {
        launched should not be empty
        stillPending shouldBe true
        List(SessionCloseCause.SocketClosed, SessionCloseCause.Crashed) should contain(closedCause(result))
        closedCause(later) shouldBe closedCause(result)
        browser.crashed should not be empty
      }
    }
  }

  private def children(): Set[ProcessHandle] = ProcessHandle.current().children().iterator().asScala.toSet

  /** Poll for an in-flight request's outcome; its own timeout bounds the wait. */
  private def waitFor(outcome: () => Option[Try[WSResponse]]): Task[Try[WSResponse]] = outcome() match {
    case Some(result) => Task.pure(result)
    case None => Task.sleep(50.millis).flatMap(_ => waitFor(outcome))
  }
}
