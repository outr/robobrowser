package spec

import rapid.Task
import robobrowser.{BrowserApp, BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}

object Test5 extends BrowserApp {
  override protected def browserConfig: RoboBrowserConfig = RoboBrowserConfig(
    browserConfig = BrowserConfig(
      headless = false
    ),
    tabSelector = TabSelector.AlwaysCreateNew
  )

  override def run(browser: RoboBrowser): Task[Unit] = for {
    _ <- browser.navigate("https://support.enovationcontrols.com/hc/en-us/articles/115000911953-ML2000-Panel-for-Engine-Controls")
    _ <- browser.waitForDetach()
  } yield()
}
