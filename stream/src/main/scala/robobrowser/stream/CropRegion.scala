package robobrowser.stream

/** Insets that carve a render target out of a full-display capture, anchored to
  * the display's top-left — where CDP device-metrics emulation paints the
  * emulated viewport. Settable on a playing element, which is what makes a
  * render-target change a reconfiguration rather than a pipeline rebuild.
  *
  * Positive insets crop, which is a `videocrop`'s whole vocabulary. Negative ones
  * add border, which only a `videobox` reads — [[CropRegion.border]] builds those
  * for a branch that has to square its target up to a fixed canvas. */
private[stream] case class CropRegion(left: Int, top: Int, right: Int, bottom: Int) {
  def launchArgs: String = s"left=$left top=$top right=$right bottom=$bottom"
}

private[stream] object CropRegion {
  def apply(display: RenderSize, target: RenderSize): CropRegion = CropRegion(
    left = 0,
    top = 0,
    right = math.max(0, display.width - target.width),
    bottom = math.max(0, display.height - target.height)
  )

  /** The border that squares an already-cropped `target` up to `canvas`'s aspect
    * ratio, centred — a `videobox`'s negative insets. A scaler can then take the
    * result to `canvas` without reshaping it, and the border is the box's own
    * black rather than whatever a driver happens to initialise a scaler's
    * surface to. Zero on every side when the two shapes already agree. */
  def border(target: RenderSize, canvas: RenderSize): CropRegion = {
    val squared = squareUp(target, canvas)
    val padX = (squared.width - target.width) / 2
    val padY = (squared.height - target.height) / 2
    CropRegion(left = -padX, top = -padY, right = -padX, bottom = -padY)
  }

  /** The smallest rectangle with `canvas`'s aspect ratio that contains `target`. */
  private def squareUp(target: RenderSize, canvas: RenderSize): RenderSize =
    if (target.width * canvas.height > canvas.width * target.height) {
      val height = math.round(target.width.toDouble * canvas.height / canvas.width).toInt
      RenderSize.even(target.width, math.max(target.height, height))
    } else {
      val width = math.round(target.height.toDouble * canvas.width / canvas.height).toInt
      RenderSize.even(math.max(target.width, width), target.height)
    }
}
