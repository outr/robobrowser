package robobrowser

import fabric.{NumDec, NumInt, Str, obj}
import rapid.Task

/** Raw pointer input via CDP `Input.dispatchMouseEvent`. Coordinates are CSS
  * pixels within the current viewport (see [[RoboBrowser.setViewportSize]]), so
  * a consumer forwarding a user's interaction into a [[Screencast]] stream maps
  * the displayed frame back to viewport coords and calls these. `buttons` is the
  * CDP pressed-button bitmask (1=left, 2=right, 4=middle). Reached as
  * `browser.mouse`. */
class Mouse(browser: RoboBrowser) {
  private def n(d: Double): NumDec = NumDec(BigDecimal(d))

  /** Pointer move. `buttons` carries any held buttons (for drags). */
  def move(x: Double, y: Double, buttons: Int = 0): Task[Unit] =
    browser.send("Input.dispatchMouseEvent", obj(
      "type" -> Str("mouseMoved"),
      "x" -> n(x), "y" -> n(y),
      "button" -> Str("none"),
      "buttons" -> NumInt(buttons.toLong)
    )).unit

  /** Button press at (x, y). */
  def press(x: Double, y: Double, button: String = "left",
            buttons: Int = 1, clickCount: Int = 1): Task[Unit] =
    browser.send("Input.dispatchMouseEvent", obj(
      "type" -> Str("mousePressed"),
      "x" -> n(x), "y" -> n(y),
      "button" -> Str(button),
      "buttons" -> NumInt(buttons.toLong),
      "clickCount" -> NumInt(clickCount.toLong)
    )).unit

  /** Button release at (x, y). */
  def release(x: Double, y: Double, button: String = "left",
              buttons: Int = 0, clickCount: Int = 1): Task[Unit] =
    browser.send("Input.dispatchMouseEvent", obj(
      "type" -> Str("mouseReleased"),
      "x" -> n(x), "y" -> n(y),
      "button" -> Str(button),
      "buttons" -> NumInt(buttons.toLong),
      "clickCount" -> NumInt(clickCount.toLong)
    )).unit

  /** Scroll wheel at (x, y). `deltaY > 0` scrolls the page down. */
  def wheel(x: Double, y: Double, deltaX: Double, deltaY: Double): Task[Unit] =
    browser.send("Input.dispatchMouseEvent", obj(
      "type" -> Str("mouseWheel"),
      "x" -> n(x), "y" -> n(y),
      "button" -> Str("none"),
      "buttons" -> NumInt(0L),
      "deltaX" -> n(deltaX), "deltaY" -> n(deltaY)
    )).unit
}
