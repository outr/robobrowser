package robobrowser.stream

import fabric.io.JsonParser
import fabric.rw._
import rapid._
import robobrowser.RoboBrowser

/** Maps DataChannel [[InputMessage]] JSON onto the existing CDP input dispatch
  * ([[robobrowser.Mouse]], [[robobrowser.input.KeyFeatures.dispatch]]).
  *
  * Coordinates arrive in transmitted-frame pixels, which is the space the
  * [[RenderPlacement]] describes: they are shifted off the frame's border and
  * scaled by the render target's size relative to the content region it occupies.
  * The render target is what the page laid out at, so this holds for an emulated
  * portrait viewport just as it does for a full-display capture, and for a
  * bordered target on a fixed-canvas branch just as it does for one filling the
  * frame. In the default setup (kiosk, scale factor 1, no encode downscale, no
  * border) it's the identity. Events are dispatched fire-and-forget: ordering is
  * preserved by the reliable+ordered DataChannel and per-event CDP sends. */
private[stream] class InputRouter(browser: RoboBrowser,
                                  placement: RenderPlacement,
                                  deviceScaleFactor: Double = 1.0) {
  private val scaleX = placement.render.width.toDouble / placement.content.width / deviceScaleFactor
  private val scaleY = placement.render.height.toDouble / placement.content.height / deviceScaleFactor

  private def pageX(streamX: Double): Double = (streamX - placement.offsetX) * scaleX
  private def pageY(streamY: Double): Double = (streamY - placement.offsetY) * scaleY

  def route(raw: String): Unit = try {
    dispatch(JsonParser(raw).as[InputMessage]).start()
  } catch {
    case t: Throwable => scribe.warn(s"Ignoring unparseable input message: ${t.getMessage}")
  }

  def dispatch(message: InputMessage): Task[Unit] = message match {
    case InputMessage.MouseMove(x, y, buttons) =>
      browser.mouse.move(pageX(x), pageY(y), buttons)
    case InputMessage.MouseDown(x, y, button, buttons, clickCount, _) =>
      browser.mouse.press(pageX(x), pageY(y), button, buttons, clickCount)
    case InputMessage.MouseUp(x, y, button, buttons, clickCount, _) =>
      browser.mouse.release(pageX(x), pageY(y), button, buttons, clickCount)
    case InputMessage.Wheel(x, y, deltaX, deltaY, _) =>
      browser.mouse.wheel(pageX(x), pageY(y), deltaX, deltaY)
    case InputMessage.KeyDown(key, code, keyCode, text, modifiers) =>
      browser.key.dispatch("keyDown", key, code, text, keyCode, modifiers)
    case InputMessage.KeyUp(key, code, keyCode, text, modifiers) =>
      browser.key.dispatch("keyUp", key, code, text, keyCode, modifiers)
    case InputMessage.Char(text, key, keyCode) =>
      browser.key.dispatch("char", key, "", text, keyCode)
  }
}
