package robobrowser

case class RoboBrowserConfig(browser: Browser = Browser.auto(),
                             browserConfig: BrowserConfig = BrowserConfig(),
                             enableRuntime: Boolean = true,
                             enablePageEvents: Boolean = true,
                             enableLifecycleEvents: Boolean = true,
                             enableDOMEvents: Boolean = true,
                             enableNetworkEvents: Boolean = true,
                             tabSelector: TabSelector = TabSelector.FirstPage)
