package spec

import rapid._
import robobrowser.display.VirtualDisplayConfig
import robobrowser.stream.Stream.stream
import robobrowser.{BrowserApp, BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}

/** Manual M2/M3 acceptance demo: streams a virtual-display browser over WebRTC.
  *
  * Run: sbt "stream/Test/runMain spec.TestStreamDemo"
  * then open http://localhost:8888 — you should see the live page, and be able
  * to click/type/scroll with native-feeling behavior. The status bar shows
  * connection state, RTT, and measured glass-to-glass latency. Ctrl+C to stop.
  * Each browser tab that connects gets its own independent stream session. */
object TestStreamDemo extends BrowserApp {
  override protected def browserConfig: RoboBrowserConfig = RoboBrowserConfig(
    browserConfig = BrowserConfig(noSandbox = true, disableGPU = true),
    tabSelector = TabSelector.FirstPage,
    virtualDisplay = Some(VirtualDisplayConfig(width = 1920, height = 1080))
  )

  override def run(browser: RoboBrowser): Task[Unit] = browser.stream.availability match {
    case Some(reason) => Task.error(new RuntimeException(s"Streaming unavailable: ${reason.message}"))
    case None =>
      val server = new StreamDemoServer(browser)
      for {
        _ <- browser.navigate("https://en.wikipedia.org/wiki/WebRTC")
        _ <- server.start()
        _ <- logger.info("Open http://localhost:8888 to view the stream (Ctrl+C to stop)")
        _ <- browser.waitForDetach()
        _ <- server.stop()
      } yield ()
  }
}
