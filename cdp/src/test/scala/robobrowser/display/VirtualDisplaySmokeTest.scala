package robobrowser.display

import rapid._
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}

import java.nio.file.Paths

/** Manual M1 smoke test: browser launches kiosk-fullscreen on a dedicated Xvfb
  * display, renders a page, cleans up display + browser on dispose.
  * Run: sbt "cdp/Test/runMain robobrowser.display.VirtualDisplaySmokeTest <out.png>" */
object VirtualDisplaySmokeTest extends RapidApp {
  override def run(args: List[String]): Task[Unit] = {
    val out = Paths.get(args.headOption.getOrElse("virtual-display-smoke.png"))
    RoboBrowser.withBrowser(RoboBrowserConfig(
      browserConfig = BrowserConfig(noSandbox = true, disableGPU = true),
      tabSelector = TabSelector.FirstPage,
      virtualDisplay = Some(VirtualDisplayConfig(width = 1280, height = 720))
    )) { browser =>
      for {
        _ <- logger.info(s"Display: ${browser.virtualDisplay.map(_.displayName)}")
        _ <- browser.navigate("https://example.com")
        _ <- browser.waitForLoaded()
        _ <- Task.sleep(scala.concurrent.duration.DurationInt(1).second)
        size <- browser.eval("return window.innerWidth + 'x' + window.innerHeight + '@' + window.devicePixelRatio")
          .map(_("result")("value").asString)
        _ <- logger.info(s"Viewport: $size")
        _ <- browser.screenshot(out)
        _ <- logger.info(s"Screenshot: $out")
      } yield ()
    }
  }
}
