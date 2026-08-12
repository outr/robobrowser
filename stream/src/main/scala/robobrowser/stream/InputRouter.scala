package robobrowser.stream

import fabric.io.JsonParser
import fabric.rw._
import rapid._
import robobrowser.RoboBrowser

/** Maps DataChannel [[InputMessage]] JSON onto the existing CDP input dispatch
  * ([[robobrowser.Mouse]], [[robobrowser.input.KeyFeatures.dispatch]]).
  *
  * Coordinates arrive in stream pixels; viewport CSS coordinates are recovered
  * as `css = stream * (renderTarget / encodedSize) / deviceScaleFactor`. The
  * render target is what the page laid out at, so this holds for an emulated
  * portrait viewport just as it does for a full-display capture. In the default
  * setup (kiosk, scale factor 1, no encode downscale) it's the identity. Events
  * are dispatched fire-and-forget: ordering is preserved by the
  * reliable+ordered DataChannel and per-event CDP sends. */
private[stream] class InputRouter(browser: RoboBrowser,
                                  target: RenderSize,
                                  encoded: RenderSize,
                                  deviceScaleFactor: Double = 1.0) {
  private val scaleX = target.width.toDouble / encoded.width / deviceScaleFactor
  private val scaleY = target.height.toDouble / encoded.height / deviceScaleFactor

  def route(raw: String): Unit = try {
    dispatch(JsonParser(raw).as[InputMessage]).start()
  } catch {
    case t: Throwable => scribe.warn(s"Ignoring unparseable input message: ${t.getMessage}")
  }

  def dispatch(message: InputMessage): Task[Unit] = message match {
    case InputMessage.MouseMove(x, y, buttons) =>
      browser.mouse.move(x * scaleX, y * scaleY, buttons)
    case InputMessage.MouseDown(x, y, button, buttons, clickCount, _) =>
      browser.mouse.press(x * scaleX, y * scaleY, button, buttons, clickCount)
    case InputMessage.MouseUp(x, y, button, buttons, clickCount, _) =>
      browser.mouse.release(x * scaleX, y * scaleY, button, buttons, clickCount)
    case InputMessage.Wheel(x, y, deltaX, deltaY, _) =>
      browser.mouse.wheel(x * scaleX, y * scaleY, deltaX, deltaY)
    case InputMessage.KeyDown(key, code, keyCode, text, modifiers) =>
      browser.key.dispatch("keyDown", key, code, text, keyCode, modifiers)
    case InputMessage.KeyUp(key, code, keyCode, text, modifiers) =>
      browser.key.dispatch("keyUp", key, code, text, keyCode, modifiers)
    case InputMessage.Char(text, key, keyCode) =>
      browser.key.dispatch("char", key, "", text, keyCode)
  }
}
