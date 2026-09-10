package spec

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import rapid.Task
import robobrowser.comm.{CommunicationManager, TargetCrashedException}
import spice.http.{ConnectionStatus, WebSocket}

import scala.concurrent.duration.DurationInt

/** The two-hour production hang, pinned: a CDP request sent before (or
  * after) `Inspector.targetCrashed` must FAIL, never wait forever. */
class TargetCrashSpec extends AnyWordSpec with Matchers {

  private class FakeSocket extends WebSocket {
    override def connect(): Task[ConnectionStatus] = Task.pure(ConnectionStatus.Open)
    override def disconnect(): Unit = ()
  }

  private class TestComm extends CommunicationManager {
    // lazy + def: the trait's initializer touches `ws` before a subclass
    // val would be assigned.
    lazy val socket: WebSocket = new FakeSocket
    override protected def ws: WebSocket = socket
  }

  private def crashFrame = """{"method":"Inspector.targetCrashed","params":{}}"""

  "CommunicationManager under target crash" should {
    "fail a pending request when the target crashes" in {
      val comm = new TestComm
      @volatile var outcome: Option[scala.util.Try[robobrowser.comm.WSResponse]] = None
      comm.send("Page.navigate").attempt.map { r => outcome = Some(r) }.start(): Unit
      // Let the send register its callback before the crash arrives.
      Task.sleep(200.millis).sync()
      comm.socket.receive.text @= crashFrame
      // The failed callback resolves promptly; never-resolving IS the bug —
      // poll with a bound so a regression fails instead of wedging the suite.
      val deadline = System.currentTimeMillis() + 2000
      while (outcome.isEmpty && System.currentTimeMillis() < deadline) Thread.sleep(50)
      val result = outcome.getOrElse(fail("request still pending after crash — the production hang"))
      result.isFailure shouldBe true
      (result.failed.get shouldBe a[TargetCrashedException]): Unit
    }
    "fail fast on any send AFTER the crash" in {
      val comm = new TestComm
      comm.socket.receive.text @= crashFrame
      Task.sleep(200.millis).sync() // let the frame route
      val result = comm.send("Page.navigate").attempt.sync()
      result.isFailure shouldBe true
      (result.failed.get shouldBe a[TargetCrashedException]): Unit
      comm.crashed shouldBe Some("Inspector.targetCrashed")
    }
  }
}
