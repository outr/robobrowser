package robobrowser.display

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/** Configuration for a per-session Xvfb virtual display (see
  * [[robobrowser.RoboBrowserConfig.virtualDisplay]]). The browser is launched
  * kiosk-fullscreen on the display, so by default viewport pixels == display
  * pixels.
  *
  * The size is also the session's upper bound: a stream can render and capture
  * any smaller rectangle of the display (see `StreamConfig.width`/`height`), but
  * growing past it needs a RandR resize that Xvfb rejects. Allocate the display
  * at the largest size the session will ever need.
  *
  * @param width        display width in pixels
  * @param height       display height in pixels
  * @param depth        color depth (bits)
  * @param baseDisplay  first X display number to try (`:100` by default, above
  *                     anything a desktop session typically uses)
  * @param startTimeout how long to wait for Xvfb to come up before failing
  */
case class VirtualDisplayConfig(width: Int = 1920,
                                height: Int = 1080,
                                depth: Int = 24,
                                baseDisplay: Int = 100,
                                startTimeout: FiniteDuration = 5.seconds)
