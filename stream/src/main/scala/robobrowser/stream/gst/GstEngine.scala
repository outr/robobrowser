package robobrowser.stream.gst

import org.freedesktop.gstreamer.{ElementFactory, Gst, State, Version}

import scala.util.Try

/** Process-wide GStreamer state: one guarded `Gst.init` per JVM (never
  * deinitialized — native state is left for JVM exit) and the encoder probe.
  *
  * `Version.of(1, 18)` is the highest version gst1-java-core 1.4.0 knows;
  * requesting it unlocks the webrtc-era API (plain `init()` requests the 1.8
  * baseline and gates features). Newer runtimes satisfy the check. */
object GstEngine {
  /** Hardware encoders first; `vaapih264enc` is the pre-1.22 VAAPI element name
    * kept for older systems; `x264enc` is the universal software fallback. */
  val encoderPreference: List[String] = List("vah264enc", "vaapih264enc", "nvh264enc", "x264enc")

  private val required: List[String] = List("ximagesrc", "webrtcbin", "h264parse", "rtph264pay")

  /** Left(error) when the native libraries fail to load/initialize. */
  lazy val initResult: Either[String, Unit] = Try {
    if (!Gst.isInitialized) {
      Gst.init(Version.of(1, 18), "robobrowser")
    }
  }.toEither.left.map(t => Option(t.getMessage).getOrElse(t.getClass.getName))

  /** ElementFactory.find throws IllegalArgumentException for missing elements
    * (verified against 1.4.0) rather than returning null. */
  def elementAvailable(name: String): Boolean = Try(ElementFactory.find(name)).isSuccess

  /** Required elements that are missing (empty when streaming can proceed). */
  lazy val missingElements: List[String] = required.filterNot(elementAvailable)

  /** The preferred usable encoder. Existence isn't enough — `vah264enc` can be
    * present yet unusable without a render node — so each candidate is smoke
    * tested with a READY state transition before being selected. */
  lazy val selectedEncoder: Option[String] = encoderPreference.find(e => elementAvailable(e) && smokeTest(e))

  private def smokeTest(name: String): Boolean = Try {
    val element = ElementFactory.make(name, s"robobrowser-probe-$name")
    try {
      val ready = element.setState(State.READY)
      element.setState(State.NULL)
      ready != org.freedesktop.gstreamer.StateChangeReturn.FAILURE
    } finally {
      element.dispose()
    }
  }.getOrElse(false)
}
