package robobrowser.window

sealed trait WindowState {
  lazy val name: String = getClass.getSimpleName.replace("$", "").toLowerCase
}

/**
 * Represents the different window states supported by the Chrome DevTools Protocol (CDP).
 * These states define the appearance and behavior of a browser window.
 */
object WindowState {
  /**
   * The default state of the browser window.
   * The window is neither minimized nor maximized, and can be resized and moved.
   */
  case object Normal extends WindowState

  /**
   * The minimized state of the browser window.
   * The window is hidden from view but remains open, typically visible in the taskbar or dock.
   */
  case object Minimized extends WindowState

  /**
   * The maximized state of the browser window.
   * The window fills the screen without entering fullscreen mode.
   * System UI elements like the title bar and taskbar remain visible.
   */
  case object Maximized extends WindowState

  /**
   * The fullscreen state of the browser window.
   * The window covers the entire screen, hiding all system UI elements such as the taskbar and title bar.
   * This state is equivalent to pressing F11 in the browser.
   */
  case object Fullscreen extends WindowState

  /**
   * The docked state of the browser window.
   * This state is rarely used in typical setups and may depend on specific configurations or extensions.
   */
  case object Docked extends WindowState
}