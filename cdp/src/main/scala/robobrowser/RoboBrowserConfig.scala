package robobrowser

import robobrowser.display.VirtualDisplayConfig

/** @param virtualDisplay when set, a dedicated Xvfb display is allocated for
  *                       this session and the browser is launched kiosk-mode
  *                       filling it (headless is overridden to false). Required
  *                       for display-capture streaming (robobrowser-stream).
  * @param freshProfile   launch on a new, empty profile: a unique temporary directory for this launch alone, in place of
  *                       `browserConfig.userDataDir`, deleted when the browser is disposed or its launch fails. No
  *                       storage, cookies or cache carry over from any other launch. */
case class RoboBrowserConfig(browser: Browser = Browser.auto(),
                             browserConfig: BrowserConfig = BrowserConfig(),
                             enableRuntime: Boolean = true,
                             enablePageEvents: Boolean = true,
                             enableLifecycleEvents: Boolean = true,
                             enableDOMEvents: Boolean = true,
                             enableNetworkEvents: Boolean = true,
                             tabSelector: TabSelector = TabSelector.FirstPage,
                             virtualDisplay: Option[VirtualDisplayConfig] = None,
                             freshProfile: Boolean = false)
