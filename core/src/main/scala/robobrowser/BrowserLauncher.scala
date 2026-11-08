package robobrowser

import rapid.Task
import scala.sys.process.*

/** Shared process launcher for the convenience CDP API and the multi-tab host. */
object BrowserLauncher {
  def createProcess(browser: Browser, config: BrowserConfig): Task[Process] = Task {
    val cmd = List(
      browser
        .existingPaths
        .headOption
        .getOrElse(throw new RuntimeException(s"Unable to find browser at ${browser.paths.mkString(", ")}"))
        .getCanonicalPath,
      s"--remote-debugging-port=${browser.port}",
      "--no-default-browser-check",
      "--no-first-run",
      "--disable-session-crashed-bubble",
      s"--remote-allow-origins=http://localhost:${browser.port}"
    ) ::: config.options
    scribe.info(cmd.mkString(" "))
    val pb = Process(cmd, None, config.env.toSeq*)
    val logger = ProcessLogger(
      line => scribe.info(line),
      line => scribe.error(line)
    )
    pb.run(logger)
  }

}
