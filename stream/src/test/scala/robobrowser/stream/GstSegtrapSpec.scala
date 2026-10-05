package robobrowser.stream

import com.sun.jna.{Library, Native}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import robobrowser.stream.gst.GstEngine

/**
 * GStreamer initialized by the engine leaves SIGSEGV to the JVM: with the segfault trap on, a plugin load installs
 * a handler that exits the process on the JVM's own safepoint-poll faults.
 *
 * Self-skips when GStreamer is unavailable.
 */
class GstSegtrapSpec extends AnyWordSpec with Matchers {
  private trait GstSegtrap extends Library {
    def gst_segtrap_is_enabled(): Boolean
  }

  "The engine's GStreamer initialization" should {
    "disable the segfault trap" in {
      GstEngine.initResult.left.toOption.foreach(e => cancel(s"Skipping: GStreamer unavailable: $e"))
      val gst = Native.load("gstreamer-1.0", classOf[GstSegtrap])
      gst.gst_segtrap_is_enabled() shouldBe false
    }
  }
}
