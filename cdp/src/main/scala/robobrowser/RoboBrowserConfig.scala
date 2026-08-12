package robobrowser

import robobrowser.display.VirtualDisplayConfig

/** @param virtualDisplay when set, a dedicated Xvfb display is allocated for
  *                       this session and the browser is launched kiosk-mode
  *                       filling it (headless is overridden to false). Required
  *                       for display-capture streaming (robobrowser-stream). */
case class RoboBrowserConfig(browser: Browser = Browser.auto(),
                             browserConfig: BrowserConfig = BrowserConfig(),
                             enableRuntime: Boolean = true,
                             enablePageEvents: Boolean = true,
                             enableLifecycleEvents: Boolean = true,
                             enableDOMEvents: Boolean = true,
                             enableNetworkEvents: Boolean = true,
                             tabSelector: TabSelector = TabSelector.FirstPage,
                             virtualDisplay: Option[VirtualDisplayConfig] = None)
