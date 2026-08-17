package robobrowser.stream

import fabric.rw._

/** Where a session's render target sits inside the frames it transmits.
  *
  * The target is scaled to fit with its aspect preserved and centred, so
  * [[content]] is the sub-rectangle carrying page pixels and everything outside
  * it is black border. A [[ResizeBehavior.Reconfigure]] branch re-pins its
  * encoder to every target, so content fills the frame; a
  * [[ResizeBehavior.FixedCanvas]] branch borders any target whose aspect differs
  * from its canvas.
  *
  * Input coordinates arrive in transmitted-frame pixels, so this is also what
  * maps them back onto the page, and it is what a consumer that wants to present
  * the content region alone crops with. */
case class RenderPlacement(render: RenderSize,
                           encoded: RenderSize,
                           offsetX: Int,
                           offsetY: Int,
                           content: RenderSize) {

  /** True when the transmitted frame carries border the render target does not
    * fill. */
  def bordered: Boolean = content != encoded
}

object RenderPlacement {
  implicit val rw: RW[RenderPlacement] = RW.gen

  /** Fit `render` inside `encoded` with its aspect preserved and centred — what
    * `vapostproc` / `videoscale` do with `add-borders=true`. */
  def fit(render: RenderSize, encoded: RenderSize): RenderPlacement = {
    val scale = math.min(encoded.width.toDouble / render.width, encoded.height.toDouble / render.height)
    val content = RenderSize.even((render.width * scale).toInt, (render.height * scale).toInt)
    RenderPlacement(
      render = render,
      encoded = encoded,
      offsetX = (encoded.width - content.width) / 2,
      offsetY = (encoded.height - content.height) / 2,
      content = content
    )
  }
}
