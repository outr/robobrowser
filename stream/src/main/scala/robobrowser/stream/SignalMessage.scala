package robobrowser.stream

import fabric._
import fabric.define.{DefType, Definition}
import fabric.dsl._
import fabric.rw._

/** WebRTC signaling messages exchanged between a [[StreamSession]] and its
  * viewer. The library never touches a websocket: consumers bridge these (as
  * JSON, via the RW below) over whatever transport they already have. The wire
  * format is `"type"`-discriminated and hand-rolled so the JavaScript client
  * can rely on it staying stable:
  *
  * {{{
  * {"type":"offer","sdp":"v=0..."}          server -> client
  * {"type":"answer","sdp":"v=0..."}         client -> server
  * {"type":"ice","sdpMLineIndex":0,"candidate":"candidate:..."}   both directions
  * {"type":"error","message":"..."}         both directions
  * {"type":"bye"}                           both directions
  * }}}
  *
  * The server always offers (webrtcbin's `on-negotiation-needed` flow); the
  * client answers. ICE candidates trickle in both directions. */
sealed trait SignalMessage

object SignalMessage {
  case class Offer(sdp: String) extends SignalMessage
  case class Answer(sdp: String) extends SignalMessage
  case class Ice(sdpMLineIndex: Int, candidate: String) extends SignalMessage
  case class Error(message: String) extends SignalMessage
  case object Bye extends SignalMessage

  implicit val rw: RW[SignalMessage] = RW.from[SignalMessage](
    r = {
      case Offer(sdp) => obj("type" -> "offer", "sdp" -> sdp)
      case Answer(sdp) => obj("type" -> "answer", "sdp" -> sdp)
      case Ice(index, candidate) => obj("type" -> "ice", "sdpMLineIndex" -> index, "candidate" -> candidate)
      case Error(message) => obj("type" -> "error", "message" -> message)
      case Bye => obj("type" -> "bye")
    },
    w = json => json("type").asString match {
      case "offer" => Offer(json("sdp").asString)
      case "answer" => Answer(json("sdp").asString)
      case "ice" => Ice(json("sdpMLineIndex").asInt, json("candidate").asString)
      case "error" => Error(json("message").asString)
      case "bye" => Bye
      case other => throw new RuntimeException(s"Unknown SignalMessage type: $other")
    },
    d = Definition(DefType.Json)
  )
}
