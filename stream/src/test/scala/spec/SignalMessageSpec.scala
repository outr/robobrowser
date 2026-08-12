package spec

import fabric.io.JsonParser
import fabric.rw._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import robobrowser.stream.SignalMessage

class SignalMessageSpec extends AnyWordSpec with Matchers {
  private def roundTrip(message: SignalMessage, expectedJson: String): Unit = {
    message.json.should(be(JsonParser(expectedJson)))
    JsonParser(expectedJson).as[SignalMessage].should(be(message))
  }

  "SignalMessage" should {
    "round-trip an offer" in {
      roundTrip(SignalMessage.Offer("v=0..."), """{"type": "offer", "sdp": "v=0..."}""")
    }
    "round-trip an answer" in {
      roundTrip(SignalMessage.Answer("v=0..."), """{"type": "answer", "sdp": "v=0..."}""")
    }
    "round-trip an ice candidate" in {
      roundTrip(
        SignalMessage.Ice(0, "candidate:842163049 1 udp"),
        """{"type": "ice", "sdpMLineIndex": 0, "candidate": "candidate:842163049 1 udp"}"""
      )
    }
    "round-trip an error" in {
      roundTrip(SignalMessage.Error("boom"), """{"type": "error", "message": "boom"}""")
    }
    "round-trip a bye" in {
      roundTrip(SignalMessage.Bye, """{"type": "bye"}""")
    }
    "fail on an unknown type" in {
      assertThrows[RuntimeException] {
        JsonParser("""{"type":"nope"}""").as[SignalMessage]
      }
    }
  }
}
