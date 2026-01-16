package robobrowser

import rapid.{RapidApp, Task}

trait BrowserApp extends RapidApp {
  protected def browserConfig: RoboBrowserConfig = RoboBrowserConfig(
    browserConfig = BrowserConfig(
      useNewHeadlessMode = false
    ),
    tabSelector = TabSelector.AlwaysCreateNew
  )

  final override def run(args: List[String]): Task[Unit] = RoboBrowser.withBrowser(
    config = browserConfig
  )(browser => run(browser))

  def run(browser: RoboBrowser): Task[Unit]
}
