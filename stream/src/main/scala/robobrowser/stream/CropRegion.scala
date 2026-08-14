package robobrowser.stream

/** Insets that carve a render target out of a full-display capture, anchored to
  * the display's top-left — where CDP device-metrics emulation paints the
  * emulated viewport. Settable on a playing `videocrop`, which is what makes a
  * render-target change a reconfiguration rather than a pipeline rebuild. */
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
}
