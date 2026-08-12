package robobrowser.display

import rapid.Task

import scala.sys.process.Process

/** A running Xvfb virtual display owned by a browser session. Obtained from
  * [[DisplayAllocator.allocate]]; disposed by [[robobrowser.RoboBrowser.dispose]]
  * (or directly) which kills the Xvfb process and releases the display number. */
class VirtualDisplay private[display](val number: Int,
                                      initialWidth: Int,
                                      initialHeight: Int,
                                      process: Process) {
  @volatile private var _width: Int = initialWidth
  @volatile private var _height: Int = initialHeight

  /** The X display name, e.g. `:100`, suitable for a `DISPLAY` env var. */
  lazy val displayName: String = s":$number"

  /** Current root-framebuffer width. Changes only through a successful
    * [[resize]] — the browser's window follows the display, not the reverse. */
  def width: Int = _width

  /** Current root-framebuffer height. */
  def height: Int = _height

  /** Resize the root framebuffer through RandR.
    *
    * Only needed to grow past the size the display was allocated at: rendering
    * and capturing a *smaller* rectangle is a capture-region concern that needs
    * nothing from the X server. Raises
    * [[DisplayResizeUnsupportedException]] when the server refuses, which is
    * what Xvfb does — its RANDR implementation advertises one fixed mode and
    * rejects `RRSetScreenSize`. */
  def resize(width: Int, height: Int): Task[Unit] = Task {
    require(width > 0 && height > 0, s"Display size must be positive, got ${width}x$height")
    if (width != _width || height != _height) {
      if (RandrResizer.resize(displayName, width, height)) {
        _width = width
        _height = height
        scribe.info(s"Resized display $displayName to ${width}x$height")
      } else {
        throw new DisplayResizeUnsupportedException(displayName, _width, _height, width, height)
      }
    }
  }

  def dispose(): Task[Unit] = Task {
    DisplayAllocator.release(number)
    process.destroy()
  }
}
