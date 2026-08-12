package spike

import org.freedesktop.gstreamer.{ElementFactory, Gst, Version}

/** Spike S1: prove the 2021 JNA bindings load against the installed GStreamer
  * runtime under a forked sbt JVM, and that element probing works.
  * Run: sbt "stream/Test/runMain spike.GstSpike1" */
object GstSpike1 {
  def main(args: Array[String]): Unit = {
    Gst.init(Version.of(1, 18), "robobrowser-spike")
    println(s"S1: Gst initialized: ${Gst.getVersionString}")
    List("ximagesrc", "webrtcbin", "vah264enc", "vaapih264enc", "nvh264enc", "x264enc", "rtph264pay", "h264parse")
      .foreach { name =>
        // ElementFactory.find throws IllegalArgumentException on missing elements
        val present = scala.util.Try(ElementFactory.find(name)).isSuccess
        println(s"S1: probe $name -> ${if (present) "present" else "MISSING"}")
      }
    val pipeline = Gst.parseLaunch("videotestsrc num-buffers=30 ! fakesink")
    pipeline.play()
    val bus = pipeline.getBus
    val eos = new java.util.concurrent.CountDownLatch(1)
    bus.connect(new org.freedesktop.gstreamer.Bus.EOS {
      override def endOfStream(source: org.freedesktop.gstreamer.GstObject): Unit = eos.countDown()
    })
    val finished = eos.await(10, java.util.concurrent.TimeUnit.SECONDS)
    pipeline.stop()
    println(s"S1: pipeline ran to EOS: $finished")
    Gst.quit()
    System.exit(if (finished) 0 else 1)
  }
}
