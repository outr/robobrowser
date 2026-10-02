package robobrowser.stream.gst

import com.sun.jna.{Library, Native, Pointer}
import org.freedesktop.gstreamer.GstObject
import org.freedesktop.gstreamer.glib.Natives

/** Holds a native reference on a GStreamer object for the length of a call, taken through the raw pointer rather
  * than the Java wrapper. Disposing the wrapper from another thread while the call runs then only drops the
  * wrapper's reference; the object is freed when the call returns. */
private[stream] object NativeHold {
  private trait GstCore extends Library {
    def gst_object_ref(obj: Pointer): Pointer
    def gst_object_unref(obj: Pointer): Unit
  }

  private lazy val gst: GstCore = Native.load("gstreamer-1.0", classOf[GstCore])

  def apply[T](obj: GstObject)(f: => T): T = {
    val pointer = Natives.getRawPointer(obj)
    gst.gst_object_ref(pointer)
    try f
    finally gst.gst_object_unref(pointer)
  }
}
