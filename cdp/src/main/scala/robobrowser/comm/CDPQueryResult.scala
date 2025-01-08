package robobrowser.comm

import fabric.rw._
import spice.net.URL

case class CDPQueryResult(description: String,
                          devtoolsFrontendUrl: URL,
                          id: String,
                          title: String,
                          `type`: String,
                          url: URL,
                          webSocketDebuggerUrl: URL)

object CDPQueryResult {
  implicit val rw: RW[CDPQueryResult] = RW.gen
}