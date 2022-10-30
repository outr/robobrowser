package com.outr.robobrowser

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fabric._
import fabric.io.JsonParser
import spice.http.{Headers, HttpMethod, HttpResponse, HttpStatus}
import spice.http.client.HttpClient
import spice.http.content.Content
import spice.net._

import java.util.Base64
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

class BrowserStack(val browser: RoboBrowser) extends AnyVal {
  private def options: BrowserStackOptions = browser.capabilities.typed[BrowserStackOptions](BrowserStack.keyName)

  def isBrowserStack: Boolean = browser.capabilities.contains(BrowserStack.keyName)

  def markAsync(status: BrowserStack.Status): IO[Json] =
    BrowserStack.mark(browser.sessionId, options.username, options.automateKey, status)

  def mark(status: BrowserStack.Status): Json = markAsync(status).unsafeRunSync()
}

object BrowserStack {
  private val keyName: String = "browserStackOptions"

  sealed trait Status

  object Status {
    case class Passed(reason: String) extends Status
    case class Failed(reason: String) extends Status
  }

  def apply[C <: Capabilities](capabilities: C, options: BrowserStackOptions): capabilities.C#C = {
    val o = options
    def t[V](key: String, value: Option[V]): Option[(String, Any)] = value.map(v => key -> v.toString)

    val bs = List(
      t("osVersion", capabilities.get("os_version")),
      t("deviceName", capabilities.get("device")),
      t("realMobile", capabilities.get("real_mobile")),
      t("projectName", Some(o.projectName)),
      t("buildName", Some(o.buildName)),
      t("sessionName", Some(o.sessionName.getOrElse(s"${capabilities.get("device").get} ${capabilities.get("browser").get}"))),
      t("local", Some(o.local)),
      t("networkLogs", Some(o.networkLogs)),
      t("idleTimeout", Some(o.idleTimeout)),
      t("appiumVersion", Some(o.appiumVersion)),
      t("consoleLogs", Some(o.consoleLogs))
    ).flatten.toMap.asJava

    capabilities.withCapabilities(
      "bstack:options" -> bs,
      BrowserStack.keyName -> o
    ).url(o.url)
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
    JsonParser(content.asString)
  }

  def status(sessionId: String, username: String, automateKey: String): IO[Json] = client(sessionId, username, automateKey)
    .get
    .send()
    .map {
      case Success(value) => value
      case Failure(exception) => throw exception
    }
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
      .map {
        case Success(value) => value
        case Failure(exception) => throw exception
      }
      .map(toJson)
  }
}