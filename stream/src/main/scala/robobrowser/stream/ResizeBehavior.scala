package robobrowser.stream

import fabric.rw._

/** How an encoder branch follows a live render-target change. */
enum ResizeBehavior {

  /** The caps pinning the encoder's input are re-pinned to each new render
    * target, so the transmitted frame is always that target. */
  case Reconfigure

  /** The caps pinning the encoder's input are fixed when the pipeline is built
    * and each render target is scaled into that canvas, bordered where the
    * aspect ratios differ. The encoder never sees a caps change. */
  case FixedCanvas
}

object ResizeBehavior {
  implicit val rw: RW[ResizeBehavior] = RW.string[ResizeBehavior](_.toString, ResizeBehavior.valueOf)
}
