package robobrowser.stream

import com.sun.jna.Pointer
import org.freedesktop.gstreamer.lowlevel.GstAPI.GstCallback
import org.freedesktop.gstreamer.{Gst, Pipeline, State}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import rapid.*
import robobrowser.stream.gst.{GstEngine, NativeHold}

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.duration.DurationInt

/**
 * A pipeline whose state change to NULL blocks — its streaming thread is held inside an element callback, so
 * deactivating the pads waits on it — torn down the way a stream session tears down: on a native lane, the caller
 * waiting a bounded time, and the wrapper disposed while the call is still blocked. The caller gets control back at
 * the bound, the blocked call finishes once the streaming thread is let go, and the process survives the object
 * being freed after that.
 *
 * Self-skips when GStreamer is unavailable.
 */
class NativeTeardownBoundSpec extends AnyWordSpec with Matchers {
  private val skipReason: Option[String] = GstEngine.initResult.left.toOption.map(e => s"GStreamer unavailable: $e")

  private val entered = new CountDownLatch(1)
  private val letGo = new CountDownLatch(1)
  private val handoff = new GstCallback {
    def callback(identity: Pointer, buffer: Pointer, userData: Pointer): Unit = {
      entered.countDown()
      letGo.await(60, TimeUnit.SECONDS)
    }
  }
  private object handoffListener

  "Tearing down a pipeline whose state change blocks" should {
    "give the caller control back at the bound and free the pipeline once the call returns" in {
      skipReason.foreach(reason => cancel(s"Skipping: $reason"))

      val pipeline = Gst.parseLaunch(
        "videotestsrc is-live=true ! identity name=block signal-handoffs=true ! fakesink sync=false"
      ).asInstanceOf[Pipeline]
      val block = pipeline.getElementByName("block")
      block.connect("handoff", classOf[AnyRef], handoffListener, handoff)
      pipeline.setState(State.PLAYING)
      entered.await(10, TimeUnit.SECONDS) shouldBe true

      val lane = new NativeLane("native-teardown-spec")
      val laneThread = new AtomicReference[String]()
      val started = System.currentTimeMillis()
      val result = lane.run(1.second) {
        laneThread.set(Thread.currentThread().getName)
        NativeHold(pipeline) {
          pipeline.setState(State.NULL)
        }
      }.sync()
      val waited = System.currentTimeMillis() - started

      result shouldBe None
      waited should be < 3000L
      laneThread.get() shouldBe "native-teardown-spec"

      // Disposed while the state change is still blocked: only the wrapper's reference goes.
      block.dispose()
      pipeline.dispose()

      letGo.countDown()
      lane.run(10.seconds)("drained").sync() shouldBe Some("drained")
      lane.shutdown()
      (1 to 3).foreach { _ =>
        System.gc()
        Thread.sleep(200L)
      }
      succeed
    }
  }
}
