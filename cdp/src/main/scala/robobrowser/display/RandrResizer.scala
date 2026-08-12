package robobrowser.display

import scala.sys.process.{Process, ProcessLogger}
import scala.util.Try

/** Resizes a running X display's root framebuffer through the RANDR extension,
  * driving the `xrandr` client rather than linking an X binding.
  *
  * Three escalating attempts, because servers differ in how much of RandR 1.2+
  * they actually implement: select an already-advertised mode, register the mode
  * and select it, then set the framebuffer size directly. Every attempt is
  * verified by re-reading the server's dimensions, so a command that reports
  * success while changing nothing (which `xrandr --newmode` does on servers
  * without `RRCreateMode`) is treated as a failure. */
private[display] object RandrResizer {
  private val Dimensions = """\s*dimensions:\s+(\d+)x(\d+)\s+pixels.*""".r
  private val Connected = """^(\S+)\s+connected.*""".r

  /** True when the display reports the requested size after the resize. */
  def resize(displayName: String, width: Int, height: Int): Boolean = {
    val mode = s"${width}x$height"
    val output = primaryOutput(displayName)
    val attempts: List[() => Unit] = List(
      () => output.foreach(o => run(displayName, List("--output", o, "--mode", mode))),
      () => output.foreach { o =>
        run(displayName, "--newmode" :: mode :: modeline(width, height))
        run(displayName, List("--addmode", o, mode))
        run(displayName, List("--output", o, "--mode", mode))
      },
      () => run(displayName, List("--fb", mode))
    )
    attempts.exists { attempt =>
      Try(attempt())
      dimensions(displayName).contains((width, height))
    }
  }

  /** The display's current root framebuffer size, when it can be read. */
  def dimensions(displayName: String): Option[(Int, Int)] =
    capture(List("xdpyinfo", "-display", displayName)).flatMap { out =>
      out.linesIterator.collectFirst {
        case Dimensions(w, h) => (w.toInt, h.toInt)
      }
    }

  private def primaryOutput(displayName: String): Option[String] =
    capture(List("xrandr", "--display", displayName)).flatMap { out =>
      out.linesIterator.collectFirst {
        case Connected(name) => name
      }
    }

  /** Timings for a 60 Hz mode whose active area is exactly the requested size.
    * Framebuffer-backed servers don't drive real hardware, so the blanking
    * intervals only need to be self-consistent. */
  private def modeline(width: Int, height: Int): List[String] = {
    val hTotal = width + 96
    val vTotal = height + 30
    val clock = BigDecimal(hTotal.toLong * vTotal.toLong * 60L) / BigDecimal(1000000)
    List(
      clock.setScale(2, BigDecimal.RoundingMode.HALF_UP).toString,
      width.toString, (width + 16).toString, (width + 48).toString, hTotal.toString,
      height.toString, (height + 3).toString, (height + 13).toString, vTotal.toString,
      "-hsync", "+vsync"
    )
  }

  private def run(displayName: String, args: List[String]): Unit = {
    val silent = ProcessLogger(_ => (), _ => ())
    Try(Process("xrandr" :: "--display" :: displayName :: args).!(silent))
    ()
  }

  private def capture(command: List[String]): Option[String] = {
    val errors = ProcessLogger(_ => (), _ => ())
    Try(Process(command).!!(errors)).toOption
  }
}
