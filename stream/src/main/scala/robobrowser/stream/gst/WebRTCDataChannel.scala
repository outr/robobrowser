package robobrowser.stream.gst

import com.sun.jna.Pointer
import org.freedesktop.gstreamer.glib.{GObject, NativeObject}
import org.freedesktop.gstreamer.lowlevel.GstAPI.GstCallback

/** Wrapper for `GstWebRTCDataChannel`, which gst1-java-core 1.4.0 does not
  * bind. Built on the binding's public extension points: registered as a GType
  * wrapper via [[RoboBrowserGstTypes]] (ServiceLoader), so webrtcbin's
  * `create-data-channel` action signal can return a typed instance through
  * `GObject.emit(classOf[WebRTCDataChannel], ...)`.
  *
  * Callback note: the anonymous [[GstCallback]] instances carry GStreamer's
  * JNA TypeMapper, so `gchararray` marshals to String automatically. Callers
  * MUST keep a reference to this object while connected — the binding retains
  * callbacks in a map keyed on the listener, and a GC'd JNA callback is a
  * native crash. Callbacks fire on GStreamer/libnice internal threads: do
  * minimal work and hop to your own executor. */
class WebRTCDataChannel(init: NativeObject.Initializer) extends GObject(init) {
  import WebRTCDataChannel._

  def sendString(message: String): Unit = emit("send-string", message)

  /** The `close` action signal (GStreamer >= 1.16) — closes the channel on the
    * wire. Distinct from `NativeObject.close()`, which frees the native handle. */
  def closeChannel(): Unit = emit("close")

  def label: String = get("label").asInstanceOf[String]

  def onMessageString(listener: OnMessageString): Unit =
    connect("on-message-string", classOf[OnMessageString], listener, new GstCallback {
      def callback(channel: Pointer, message: String): Unit = listener.onMessage(message)
    })

  def onOpen(listener: OnOpen): Unit =
    connect("on-open", classOf[OnOpen], listener, new GstCallback {
      def callback(channel: Pointer): Unit = listener.onOpen()
    })

  def onClose(listener: OnClose): Unit =
    connect("on-close", classOf[OnClose], listener, new GstCallback {
      def callback(channel: Pointer): Unit = listener.onClose()
    })

  def onError(listener: OnError): Unit =
    connect("on-error", classOf[OnError], listener, new GstCallback {
      // second arg is a GError*; details stay native, existence is the signal
      def callback(channel: Pointer, error: Pointer): Unit = listener.onError()
    })
}

object WebRTCDataChannel {
  trait OnMessageString {
    def onMessage(message: String): Unit
  }
  trait OnOpen {
    def onOpen(): Unit
  }
  trait OnClose {
    def onClose(): Unit
  }
  trait OnError {
    def onError(): Unit
  }
}
