package robobrowser

import fabric.{NumInt, Str, obj}
import rapid.Task
import robobrowser.event.ScreencastFrameEvent

/** Live screen streaming over CDP `Page.startScreencast`.
  *
  * Chrome emits a `Page.screencastFrame` whenever the page visually changes
  * (it is change-driven, not a fixed frame rate — a static page streams almost
  * nothing). Every frame MUST be acked (`Page.screencastFrameAck` with its
  * `sessionId`) or Chrome stalls the stream after a few buffered frames; this
  * feature acks automatically and hands each frame to [[start]]'s callback.
  *
  * Pair with [[RoboBrowser.setViewportSize]] to control the rendered size, and
  * with the `Input.dispatch*` CDP methods (via [[RoboBrowser.send]]) to forward
  * user interaction back into the streamed tab. Reached as `browser.screencast`. */
class Screencast(browser: RoboBrowser) {
  @volatile private var callback: Option[ScreencastFrameEvent => Unit] = None
  @volatile private var attached: Boolean = false

  /** Attach the frame listener exactly once (lazily — browsers that never
    * screencast pay nothing). The handler acks every frame so the stream keeps
    * flowing, then forwards to the active callback (a no-op while stopped, so
    * the single registration is safe to leave in place across start/stop). */
  private def ensureAttached(): Unit = synchronized {
    if (!attached) {
      attached = true
      browser.event.page.screencastFrame.attach { frame =>
        browser.send("Page.screencastFrameAck", obj("sessionId" -> NumInt(frame.sessionId))).start()
        callback.foreach(cb => cb(frame))
      }
    }
  }

  /** Start (or restart) streaming. `onFrame` is invoked for every frame, which
    * has already been acked. `maxWidth`/`maxHeight` cap the streamed image size
    * (Chrome scales the page render down to fit, preserving aspect); omit for
    * full viewport resolution. `everyNthFrame` drops frames to trade smoothness
    * for bandwidth (1 = every frame). */
  def start(onFrame: ScreencastFrameEvent => Unit,
            format: String = "jpeg",
            quality: Int = 70,
            maxWidth: Option[Int] = None,
            maxHeight: Option[Int] = None,
            everyNthFrame: Int = 1): Task[Unit] = {
    ensureAttached()
    callback = Some(onFrame)
    val base: List[(String, fabric.Json)] = List(
      "format" -> Str(format),
      "quality" -> NumInt(quality.toLong),
      "everyNthFrame" -> NumInt(everyNthFrame.toLong)
    )
    val sized: List[(String, fabric.Json)] =
      maxWidth.map(w => "maxWidth" -> NumInt(w.toLong)).toList :::
        maxHeight.map(h => "maxHeight" -> NumInt(h.toLong)).toList
    browser.send("Page.enable")
      .flatMap(_ => browser.send("Page.startScreencast", obj((base ::: sized)*)))
      .unit
  }

  /** Stop streaming. The frame listener stays attached but goes idle (no
    * callback), so a later [[start]] resumes without re-registering. */
  def stop(): Task[Unit] = {
    callback = None
    browser.send("Page.stopScreencast").unit
  }
}
