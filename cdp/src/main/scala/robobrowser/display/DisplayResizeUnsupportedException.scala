package robobrowser.display

/** Raised when an X server refuses to change the size of a virtual display's
  * root framebuffer.
  *
  * Xvfb builds the RANDR extension but exposes a single fixed mode whose size is
  * the framebuffer it was spawned with, and rejects `RRSetScreenSize` outright —
  * so a display started at 1920x1080 stays 1920x1080 for its lifetime. There is
  * no in-place recovery: restarting Xvfb on the same display number severs the
  * X connection of every client on it, which kills the browser rendering there.
  *
  * Callers that need a larger surface allocate the display at that size when the
  * browser launches. Rendering *smaller* than the display never reaches this
  * path — that is a capture-region change, which needs no server support. */
class DisplayResizeUnsupportedException(val displayName: String,
                                        val currentWidth: Int,
                                        val currentHeight: Int,
                                        val requestedWidth: Int,
                                        val requestedHeight: Int,
                                        cause: Option[Throwable] = None)
  extends RuntimeException(
    s"X display $displayName cannot be resized from ${currentWidth}x$currentHeight to " +
      s"${requestedWidth}x$requestedHeight: the server rejected the RandR resize. Allocate the " +
      "display at the larger size when launching the browser (VirtualDisplayConfig.width/height); " +
      "sizes within the current framebuffer need no resize.",
    cause.orNull)
