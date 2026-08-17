package robobrowser.stream

import fabric.rw._

/** A pixel size — used for the virtual display's framebuffer, the render/capture
  * target within it, and the encoded video frame. */
case class RenderSize(width: Int, height: Int) {
  def fitsWithin(bounds: RenderSize): Boolean = width <= bounds.width && height <= bounds.height

  override def toString: String = s"${width}x$height"
}

object RenderSize {
  implicit val rw: RW[RenderSize] = RW.gen

  /** H.264 4:2:0 chroma subsampling requires even dimensions, so every size that
    * reaches an encoder is rounded down to the nearest even value (floored at 2). */
  def even(width: Int, height: Int): RenderSize = {
    def round(v: Int): Int = math.max(2, (v / 2) * 2)
    RenderSize(round(width), round(height))
  }
}
