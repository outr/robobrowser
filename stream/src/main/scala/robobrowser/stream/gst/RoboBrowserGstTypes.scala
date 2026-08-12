package robobrowser.stream.gst

import org.freedesktop.gstreamer.glib.{Natives, NativeObject}

/** Registers our GType wrappers with gst1-java-core's type registry, mirroring
  * how the binding's own `WebRTC$Types` registers `GstWebRTCBin`. Discovered
  * via ServiceLoader — see
  * `META-INF/services/org.freedesktop.gstreamer.glib.NativeObject$TypeProvider`. */
class RoboBrowserGstTypes extends NativeObject.TypeProvider {
  override def types(): java.util.stream.Stream[NativeObject.TypeRegistration[?]] =
    java.util.stream.Stream.of(
      Natives.registration(classOf[WebRTCDataChannel], "GstWebRTCDataChannel", init => new WebRTCDataChannel(init))
    )
}
