package robobrowser.display

import rapid.Task

import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*
import scala.sys.process.{Process, ProcessLogger}

/** Allocates per-session Xvfb displays (`:100 + n`) so each browser's pixels can
  * be captured independently. Display numbers are claimed against both the
  * host's X lock files/sockets and a JVM-local claim set (atomic for concurrent
  * in-JVM allocations); a cross-process race is detected when Xvfb exits before
  * its socket appears, and the next number is tried. A JVM shutdown hook kills
  * any Xvfb processes still alive so abrupt exits don't leak displays. */
object DisplayAllocator {
  private val MaxAttempts = 50

  private val claimed = scala.collection.mutable.Set.empty[Int]
  private val live = new ConcurrentHashMap[Int, Process]()
  private lazy val shutdownHook: Unit = Runtime.getRuntime.addShutdownHook(new Thread(() => {
    live.values().asScala.foreach(_.destroy())
  }))

  def allocate(config: VirtualDisplayConfig): Task[VirtualDisplay] = Task {
    shutdownHook
    allocateRecursive(config, config.baseDisplay, attempts = 0)
  }

  private def allocateRecursive(config: VirtualDisplayConfig, from: Int, attempts: Int): VirtualDisplay = {
    if (attempts >= MaxAttempts) {
      throw new RuntimeException(s"Unable to allocate an X display after $MaxAttempts attempts (started at :${config.baseDisplay})")
    }
    val number = claim(from)
    val process = try {
      spawn(number, config)
    } catch {
      case t: Throwable =>
        release(number)
        throw t
    }
    if (awaitReady(number, process, config)) {
      live.put(number, process)
      new VirtualDisplay(number, config.width, config.height, process)
    } else if (process.isAlive()) {
      // Xvfb is running but its socket never appeared — give up rather than loop
      process.destroy()
      release(number)
      throw new RuntimeException(s"Xvfb :$number did not become ready within ${config.startTimeout}")
    } else {
      // Lost a cross-process race for the display number — try the next one
      release(number)
      allocateRecursive(config, number + 1, attempts + 1)
    }
  }

  /** Claim the first free display number >= `from`, atomically within this JVM. */
  private def claim(from: Int): Int = synchronized {
    val number = nextFree(from, claimed.toSet)
    claimed += number
    number
  }

  private[display] def nextFree(from: Int, excluded: Set[Int], tmpDir: Path = Paths.get("/tmp")): Int =
    Iterator.from(from).filterNot(n => excluded.contains(n) || isDisplayTaken(n, tmpDir)).next()

  private[display] def release(number: Int): Unit = {
    synchronized {
      claimed -= number
    }
    live.remove(number)
  }

  private[display] def isDisplayTaken(number: Int, tmpDir: Path = Paths.get("/tmp")): Boolean =
    Files.exists(tmpDir.resolve(s".X$number-lock")) || Files.exists(socketPath(number, tmpDir))

  private def socketPath(number: Int, tmpDir: Path = Paths.get("/tmp")): Path =
    tmpDir.resolve(".X11-unix").resolve(s"X$number")

  private def spawn(number: Int, config: VirtualDisplayConfig): Process = {
    val cmd = List(
      "Xvfb",
      s":$number",
      "-screen", "0", s"${config.width}x${config.height}x${config.depth}",
      // Explicit so the display always reports its geometry through RandR, which
      // is how a resize is attempted and verified
      "+extension", "RANDR",
      "-nolisten", "tcp"
    )
    scribe.info(cmd.mkString(" "))
    val logger = ProcessLogger(
      line => scribe.info(line),
      line => scribe.error(line)
    )
    try {
      Process(cmd).run(logger)
    } catch {
      case t: java.io.IOException =>
        throw new RuntimeException("Unable to launch Xvfb. Install it (apt: xvfb, pacman: xorg-server-xvfb, " +
          "dnf: xorg-x11-server-Xvfb) — see the README's headful-via-Xvfb section.", t)
    }
  }

  /** Wait for the display's X socket to appear. Returns false if it never does —
    * either Xvfb died (cross-process race for the number) or it hung. */
  private def awaitReady(number: Int, process: Process, config: VirtualDisplayConfig): Boolean = {
    val deadline = System.currentTimeMillis() + config.startTimeout.toMillis
    val socket = socketPath(number)
    var ready = Files.exists(socket)
    while (!ready && System.currentTimeMillis() < deadline && process.isAlive()) {
      Thread.sleep(50)
      ready = Files.exists(socket)
    }
    ready
  }
}
