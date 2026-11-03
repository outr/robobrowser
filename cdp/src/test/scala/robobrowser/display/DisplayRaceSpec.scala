package robobrowser.display

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Paths}
import scala.sys.process.Process

/** Two processes starting Xvfb on the same display number at once: the one that loses must not take the winner's
  * display for its own. Needs Xvfb; skipped without it. */
class DisplayRaceSpec extends AnyWordSpec with Matchers {

  private val xvfb: Boolean =
    sys.env.getOrElse("PATH", "").split(java.io.File.pathSeparatorChar).exists(d => new java.io.File(d, "Xvfb").canExecute)

  /** Xvfb on `number` started by another process tree (as another JVM's would be): its pid. */
  private def foreignXvfb(number: Int): Long =
    Process(Seq("sh", "-c", s"Xvfb :$number -nolisten tcp >/dev/null 2>&1 & echo $$!")).!!.trim.toLong

  "a display another process's Xvfb holds" should {
    "not be taken as ready by an Xvfb that lost the race for it, and the allocator moves to a display of its own" in {
      if (!xvfb) cancel("Xvfb not installed")
      val number = DisplayAllocator.nextFree(400, Set.empty)
      val foreign = foreignXvfb(number)
      try {
        val socket = Paths.get("/tmp/.X11-unix").resolve(s"X$number")
        val deadline = System.currentTimeMillis() + 10000
        while (!Files.exists(socket) && System.currentTimeMillis() < deadline) Thread.sleep(20)
        Files.exists(socket) shouldBe true
        val config = VirtualDisplayConfig(width = 320, height = 240, baseDisplay = number)
        val ours = DisplayAllocator.spawn(number, config)
        try DisplayAllocator.awaitReady(number, ours, config) shouldBe false
        finally ours.destroy()
        val own = DisplayAllocator.allocate(config).sync()
        try own.number should not be number
        finally own.dispose().sync()
      } finally ProcessHandle.of(foreign).ifPresent(_.destroy())
    }
  }
}
