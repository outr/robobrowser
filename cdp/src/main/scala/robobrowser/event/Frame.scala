package robobrowser.event

import fabric.rw._

case class Frame(id: String,
                 loaderId: String,
                 url: String,
                 domainAndRegistry: String,
                 securityOrigin: String,
                 mimeType: String,
                 adFrameStatus: AdFrameStatus,
                 secureContextType: String,
                 crossOriginIsolatedContextType: String,
                 gatedAPIFeatures: List[String])

object Frame {
  implicit val rw: RW[Frame] = RW.gen
}