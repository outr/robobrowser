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
  * in-JVM allocations); a cross-process race is detected when the display's lock
  * names another process's X server or Xvfb exits before its socket appears,
  * and the next number is tried. A JVM shutdown hook kills
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

  /** Whether display `number` is in use: its lock names a live process, or it has a socket and no lock. A lock left by
    * a process that is gone (a container restarted) does not hold the number: Xvfb replaces a stale lock itself. */
  private[display] def isDisplayTaken(number: Int, tmpDir: Path = Paths.get("/tmp")): Boolean = {
    val lock = tmpDir.resolve(s".X$number-lock")
    if (Files.exists(lock)) lockHolderAlive(lock)
    else Files.exists(socketPath(number, tmpDir))
  }

  /** Whether the process a display lock names is alive; true when the lock cannot be read, as the safe answer. */
  private def lockHolderAlive(lock: Path): Boolean =
    scala.util.Try(new String(Files.readAllBytes(lock)).trim.toLong).toOption match {
      case Some(pid) => java.lang.ProcessHandle.of(pid).map[Boolean](_.isAlive).orElse(false)
      case None => true
    }

  private def socketPath(number: Int, tmpDir: Path = Paths.get("/tmp")): Path =
    tmpDir.resolve(".X11-unix").resolve(s"X$number")

  private[display] def spawn(number: Int, config: VirtualDisplayConfig): Process = {
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

  /** Wait until display `number` is this Xvfb's: its socket is up and its lock names this process (or a process under
    * it). A socket alone proves nothing — an Xvfb that lost a cross-process race for the number sees the winner's
    * socket until it exits. Returns false when the display never becomes this Xvfb's: it died, another process's X
    * server holds the number (this Xvfb is then ended, so the caller tries the next number), or it hung. */
  private[display] def awaitReady(number: Int, process: Process, config: VirtualDisplayConfig, tmpDir: Path = Paths.get("/tmp")): Boolean = {
    val deadline = System.currentTimeMillis() + config.startTimeout.toMillis
    val socket = socketPath(number, tmpDir)
    val lock = tmpDir.resolve(s".X$number-lock")
    def holder: Option[Long] = scala.util.Try(new String(Files.readAllBytes(lock)).trim.toLong).toOption
    def ours(pid: Long): Boolean = java.lang.ProcessHandle.current().descendants().anyMatch(_.pid() == pid)
    def alive(pid: Long): Boolean = java.lang.ProcessHandle.of(pid).map[Boolean](_.isAlive).orElse(false)
    var outcome: Option[Boolean] = None
    while (outcome.isEmpty && System.currentTimeMillis() < deadline) {
      if (Files.exists(socket)) holder match {
        case Some(pid) if ours(pid) => outcome = Some(true)
        case Some(pid) if alive(pid) => outcome = Some(false)
        case _ => ()
      }
      if (outcome.isEmpty && !process.isAlive()) outcome = Some(false)
      if (outcome.isEmpty) Thread.sleep(50)
    }
    if (outcome.contains(false) && process.isAlive()) {
      process.destroy()
      process.exitValue()
    }
    outcome.getOrElse(false)
  }
}
