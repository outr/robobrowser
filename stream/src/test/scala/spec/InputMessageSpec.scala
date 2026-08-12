package spec

import fabric.io.JsonParser
import fabric.rw._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import robobrowser.stream.InputMessage

class InputMessageSpec extends AnyWordSpec with Matchers {
  private def roundTrip(message: InputMessage, expectedJson: String): Unit = {
    message.json.should(be(JsonParser(expectedJson)))
    JsonParser(expectedJson).as[InputMessage].should(be(message))
  }

  "InputMessage" should {
    "round-trip a mouse move" in {
      roundTrip(
        InputMessage.MouseMove(1.5, 2.5, 1),
        """{"type": "mousemove", "x": 1.5, "y": 2.5, "buttons": 1}"""
      )
    }
    "round-trip a mouse down" in {
      roundTrip(
        InputMessage.MouseDown(10.0, 20.0, "left", 1, 1, 0),
        """{"type": "mousedown", "x": 10.0, "y": 20.0, "button": "left", "buttons": 1, "clickCount": 1, "modifiers": 0}"""
      )
    }
    "round-trip a mouse up" in {
      roundTrip(
        InputMessage.MouseUp(10.0, 20.0, "right", 0, 1, 2),
        """{"type": "mouseup", "x": 10.0, "y": 20.0, "button": "right", "buttons": 0, "clickCount": 1, "modifiers": 2}"""
      )
    }
    "round-trip a wheel" in {
      roundTrip(
        InputMessage.Wheel(5.0, 6.0, 0.0, 120.0, 0),
        """{"type": "wheel", "x": 5.0, "y": 6.0, "deltaX": 0.0, "deltaY": 120.0, "modifiers": 0}"""
      )
    }
    "round-trip a key down" in {
      roundTrip(
        InputMessage.KeyDown("a", "KeyA", 65, "", 0),
        """{"type": "keydown", "key": "a", "code": "KeyA", "keyCode": 65, "text": "", "modifiers": 0}"""
      )
    }
    "round-trip a key up with modifiers" in {
      roundTrip(
        InputMessage.KeyUp("A", "KeyA", 65, "", 8),
        """{"type": "keyup", "key": "A", "code": "KeyA", "keyCode": 65, "text": "", "modifiers": 8}"""
      )
    }
    "round-trip a char" in {
      roundTrip(
        InputMessage.Char("a", "a", 65),
        """{"type": "char", "text": "a", "key": "a", "keyCode": 65}"""
      )
    }
    "apply defaults for omitted client fields" in {
      JsonParser("""{"type": "mousedown", "x": 1, "y": 2}""").as[InputMessage]
        .should(be(InputMessage.MouseDown(1.0, 2.0, "left", 1, 1, 0)))
    }
  }
}
