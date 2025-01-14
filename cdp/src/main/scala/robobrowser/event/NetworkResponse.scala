package robobrowser.event

import fabric.rw._

case class NetworkResponse(url: String,
                           status: Int,
                           statusText: String,
                           headers: Map[String, String],
                           mimeType: String,
                           charset: String,
                           connectionReused: Boolean,
                           connectionId: Int,
                           fromDiskCache: Boolean,
                           fromServiceWorker: Boolean,
                           fromPrefetchCache: Boolean,
                           encodedDataLength: Long,
                           protocol: String,
                           alternateProtocolUsage: Option[String],
                           securityState: String)

object NetworkResponse {
  implicit val rw: RW[NetworkResponse] = RW.gen
}