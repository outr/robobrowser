package com.outr.robobrowser.browserstack

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.outr.robobrowser.RoboBrowser
import fabric.io.JsonParser
import fabric._
import spice.http.{Headers, HttpMethod, HttpResponse, HttpStatus}
import spice.http.client.HttpClient
import spice.http.content.Content
import spice.net._

import java.util.Base64
import scala.util.{Failure, Success}

case class BrowserStack(browser: RoboBrowser) extends AnyVal {
  private def options: BrowserStackOptions = browser.capabilities.getCapability(BrowserStack.keyName).asInstanceOf[BrowserStackOptions]

  def isBrowserStack: Boolean = Option(browser.capabilities.getCapability(BrowserStack.keyName)).nonEmpty

  def markAsync(status: BrowserStack.Status): IO[Json] =
    BrowserStack.mark(browser.sessionId, options.username, options.automateKey, status)

  def mark(status: BrowserStack.Status): Json = markAsync(status).unsafeRunSync()
}

object BrowserStack {
  val keyName: String = "browserStackOptions"

  sealed trait Status

  object Status {
    case class Passed(reason: String) extends Status
    case class Failed(reason: String) extends Status
  }

  def url(username: String, automateKey: String): URL = URL(
    protocol = Protocol.Https,
    host = s"$username:$automateKey@hub-cloud.browserstack.com",
    port = 443,
    path = path"/wd/hub"
  )

  private def encoded(username: String, automateKey: String): String = {
    new String(Base64.getEncoder.encode(s"$username:$automateKey".getBytes("UTF-8")), "UTF-8")
  }

  private def client(sessionId: String, username: String, password: String): HttpClient = HttpClient
    .url(url"https://api-cloud.browserstack.com".withPath(s"/automate/sessions/$sessionId.json"))
    .header(Headers.Request.Authorization(s"Basic ${encoded(username, password)}"))

  private def toJson(response: HttpResponse): Json = {
    if (response.status != HttpStatus.OK) throw new RuntimeException(s"Error: ${response.status} - ${response.content.map(_.asString)}")
    val content = response.content.getOrElse(
      throw new RuntimeException(s"No content for request: ${response.status}")
    )
    JsonParser(content.asString.unsafeRunSync())
  }

  def status(sessionId: String, username: String, automateKey: String): IO[Json] = client(sessionId, username, automateKey)
    .get
    .send()
    .map(toJson)

  def mark(sessionId: String, username: String, automateKey: String, status: Status): IO[Json] = {
    val json = status match {
      case Status.Passed(reason) => obj("status" -> "passed", "reason" -> reason)
      case Status.Failed(reason) => obj("status" -> "failed", "reason" -> reason)
    }
    client(sessionId, username, automateKey)
      .method(HttpMethod.Put)
      .content(Content.json(json))
      .send()
      .map(toJson)
  }
}