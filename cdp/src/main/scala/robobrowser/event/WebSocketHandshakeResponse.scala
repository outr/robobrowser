package robobrowser.event

import fabric.rw._

case class WebSocketHandshakeResponse(status: Int,
                                      statusText: String,
                                      headers: Map[String, String],
                                      headersText: Option[String],
                                      requestHeaders: Option[Map[String, String]],
                                      requestHeadersText: Option[String])

object WebSocketHandshakeResponse {
  implicit val rw: RW[WebSocketHandshakeResponse] = RW.gen
}
