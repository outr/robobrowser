package robobrowser.fetch

import fabric._
import fabric.dsl._
import fabric.rw._
import rapid.Task
import robobrowser.comm.CommunicationManager
import spice.http.{Headers, HttpMethod}

class Fetch(cm: CommunicationManager) {
  private def headers2Json(headers: Option[Headers]): Json = headers.map(_.map.toList.map {
    case (name, value) => obj(
      "name" -> name,
      "value" -> value.mkString(", ")
    )
  }).json

  def continueRequest(requestId: RequestId,
                      url: Option[String] = None,
                      method: Option[HttpMethod] = None,
                      postData: Option[String] = None,
                      headers: Option[Headers] = None): Task[Unit] =
    cm.send(
      method = "Fetch.continueRequest",
      params = obj(
        "requestId" -> requestId.value,
        "url" -> url.json,
        "method" -> method.map(_.value).json,
        "postData" -> postData.json,
        "headers" -> headers2Json(headers)
      )
    ).map { response =>
      scribe.info(s"Response: $response")
    }.unit

  def continueWithAuth(requestId: RequestId,
                       response: String,
                       username: Option[String],
                       password: Option[String]): Task[Unit] = cm.send(
    method = "Fetch.continueWithAuth",
    params = obj(
      "requestId" -> requestId.value,
      "authChallengeResponse" -> obj(
        "response" -> response,
        "username" -> username.json,
        "password" -> password.json
      )
    )
  ).unit

  def disable: Task[Unit] = cm.send("Fetch.disable").unit

  def enable(patterns: List[RequestPattern], handleAuthRequests: Boolean = false): Task[Unit] =
    cm.send(
      method = "Fetch.enable",
      params = obj(
        "patterns" -> patterns.json,
        "handleAuthRequests" -> handleAuthRequests
      )
    ).unit

  def failRequest(requestId: RequestId, errorReason: ErrorReason): Task[Unit] = cm.send(
    method = "Fetch.failRequest",
    params = obj(
      "requestId" -> requestId.value,
      "errorReason" -> errorReason.json
    )
  ).unit

  def fulfillRequest(requestId: RequestId,
                     responseCode: Int,
                     responseHeaders: Option[Headers] = None,
                     binaryResponseHeaders: Option[String] = None,
                     body: Option[String] = None,
                     responsePhrase: Option[String] = None): Task[Unit] = cm.send(
    method = "Fetch.fulfillRequest",
    params = obj(
      "requestId" -> requestId.value,
      "responseCode" -> responseCode,
      "responseHeaders" -> headers2Json(responseHeaders),
      "bindaryResponseHeaders" -> binaryResponseHeaders.json,
      "body" -> body.json,
      "responsePhrase" -> responsePhrase.json
    )
  ).unit

  def getResponseBody(requestId: RequestId): Task[ResponseBody] = cm.send(
    method = "Fetch.getResponseBody",
    params = obj(
      "requestId" -> requestId.value
    )
  ).map { response =>
    response.result.as[ResponseBody]
  }

  def takeResponseBodyAsStream(requestId: RequestId): Task[String] = cm.send(
    method = "Fetch.takeResponseBodyAsStream",
    params = obj(
      "requestId" -> requestId.value
    )
  ).map { response =>
    response.result("stream").asString
  }
}

case class RequestId(value: String) extends AnyVal

object RequestId {
  implicit val rw: RW[RequestId] = RW.string(
    asString = _.value,
    fromString = s => RequestId(s)
  )
}

case class RequestPattern(urlPattern: String = "*",
                          resourceType: Option[ResourceType] = None,
                          requestStage: Option[RequestStage] = None)

object RequestPattern {
  implicit val rw: RW[RequestPattern] = RW.gen
}

sealed trait ResourceType

object ResourceType {
  implicit val rw: RW[ResourceType] = RW.gen

  case object Document extends ResourceType
  case object Stylesheet extends ResourceType
  case object Image extends ResourceType
  case object Media extends ResourceType
  case object Font extends ResourceType
  case object Script extends ResourceType
  case object TextTrack extends ResourceType
  case object XHR extends ResourceType
  case object Fetch extends ResourceType
  case object Prefetch extends ResourceType
  case object EventSource extends ResourceType
  case object WebSocket extends ResourceType
  case object Manifest extends ResourceType
  case object SignedExchange extends ResourceType
  case object Ping extends ResourceType
  case object CSPViolationReport extends ResourceType
  case object Preflight extends ResourceType
  case object FedCM extends ResourceType
  case object Other extends ResourceType
}

sealed trait RequestStage

object RequestStage {
  implicit val rw: RW[RequestStage] = RW.gen

  case object Request extends RequestStage
  case object Response extends RequestStage
}

sealed trait ErrorReason

object ErrorReason {
  implicit val rw: RW[ErrorReason] = RW.gen

  case object Failed extends ErrorReason
  case object Aborted extends ErrorReason
  case object TimedOut extends ErrorReason
  case object AccessDenied extends ErrorReason
  case object ConnectionClosed extends ErrorReason
  case object ConnectionReset extends ErrorReason
  case object ConnectionRefused extends ErrorReason
  case object ConnectionAborted extends ErrorReason
  case object ConnectionFailed extends ErrorReason
  case object NameNotResolved extends ErrorReason
  case object InternetDisconnected extends ErrorReason
  case object AddressUnreachable extends ErrorReason
  case object BlockedByClient extends ErrorReason
  case object BlockedByResponse extends ErrorReason
}

case class ResponseBody(body: String, base64Encoded: Boolean)

object ResponseBody {
  implicit val rw: RW[ResponseBody] = RW.gen
}

case class AuthChallenge(source: Option[String],
                         origin: String,
                         scheme: String,
                         realm: String)

object AuthChallenge {
  implicit val rw: RW[AuthChallenge] = RW.gen
}