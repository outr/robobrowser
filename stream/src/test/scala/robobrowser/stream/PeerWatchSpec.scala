package robobrowser.stream

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*

/** When a viewer counts as gone, and how a failure reads against its transport. */
class PeerWatchSpec extends AnyWordSpec with Matchers {
  private val Grace = 300.millis

  private def watch(): (PeerWatch, ConcurrentLinkedQueue[String]) = {
    val reasons = new ConcurrentLinkedQueue[String]()
    (new PeerWatch(Grace, reasons.add(_)), reasons)
  }

  private def connected(): (PeerWatch, ConcurrentLinkedQueue[String]) = {
    val (w, reasons) = watch()
    w.peer(PeerConnectionState.Connecting)
    w.peer(PeerConnectionState.Connected)
    w.channelOpened()
    (w, reasons)
  }

  "a failure" should {
    "be a fault while the viewer is connected and its channel open" in {
      val (w, reasons) = connected()
      w.classify shouldBe PipelineFailure.Fault
      w.writable shouldBe true
      reasons shouldBe empty
    }
    "be a fault before the viewer ever connected" in {
      val (w, _) = watch()
      w.classify shouldBe PipelineFailure.Fault
      w.peer(PeerConnectionState.Connecting)
      w.classify shouldBe PipelineFailure.Fault
      w.writable shouldBe false
    }
    "be the viewer leaving once the peer connection is disconnected, failed or closed" in {
      List(PeerConnectionState.Disconnected, PeerConnectionState.Failed, PeerConnectionState.Closed).foreach { state =>
        val (w, _) = connected()
        w.peer(state)
        withClue(s"$state: ") {
          w.classify shouldBe PipelineFailure.PeerGone
          w.writable shouldBe false
        }
      }
    }
    "be the viewer leaving once its DataChannel closed" in {
      val (w, _) = connected()
      w.channelClosed()
      w.classify shouldBe PipelineFailure.PeerGone
      w.writable shouldBe false
    }
  }

  "the viewer" should {
    "be gone at once when the peer connection fails or closes, and only once" in {
      val (w, reasons) = connected()
      w.peer(PeerConnectionState.Failed)
      w.peer(PeerConnectionState.Closed)
      w.channelClosed()
      reasons.asScala.toList shouldBe List("peer connection failed")
      w.departed shouldBe true
    }
    "be gone at once when its DataChannel closes after opening" in {
      val (w, reasons) = connected()
      w.channelClosed()
      reasons.asScala.toList shouldBe List("data channel closed")
    }
    "not be gone when a channel that never opened closes" in {
      val (w, reasons) = watch()
      w.channelClosed()
      reasons shouldBe empty
      w.departed shouldBe false
    }
    "be gone when a write fails after the transport went down" in {
      val (w, reasons) = connected()
      w.failed("sctpenc0: Could not write to resource.")
      w.failed("sctpenc0: Could not write to resource.")
      reasons.asScala.toList shouldBe List("sctpenc0: Could not write to resource.")
    }
    "be gone once the peer connection stays disconnected past the grace" in {
      val (w, reasons) = connected()
      w.peer(PeerConnectionState.Disconnected)
      reasons shouldBe empty
      Thread.sleep((Grace + 400.millis).toMillis)
      reasons.asScala.toList shouldBe List(s"peer connection disconnected for $Grace")
    }
    "stay when the peer connection recovers within the grace" in {
      val (w, reasons) = connected()
      w.peer(PeerConnectionState.Disconnected)
      Thread.sleep(Grace.toMillis / 3)
      w.peer(PeerConnectionState.Connected)
      Thread.sleep((Grace + 400.millis).toMillis)
      reasons shouldBe empty
      w.departed shouldBe false
      w.writable shouldBe true
    }
    "not be gone by an earlier disconnect when a later one is still within its grace" in {
      val (w, reasons) = connected()
      w.peer(PeerConnectionState.Disconnected)
      Thread.sleep(Grace.toMillis * 2 / 3)
      w.peer(PeerConnectionState.Connected)
      w.peer(PeerConnectionState.Disconnected)
      Thread.sleep(Grace.toMillis / 2)
      reasons shouldBe empty
      Thread.sleep(Grace.toMillis)
      reasons.asScala.toList shouldBe List(s"peer connection disconnected for $Grace")
    }
  }
}
