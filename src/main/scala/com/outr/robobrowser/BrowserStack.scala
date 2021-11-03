package com.outr.robobrowser

import fabric.{Value, obj}
import fabric.parse.Json
import io.youi.client.HttpClient
import io.youi.http.content.Content
import io.youi.http.{Headers, HttpMethod, HttpResponse, HttpStatus}
import io.youi.net._
import org.openqa.selenium.chrome.ChromeOptions

import java.util.Base64
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters._

trait BrowserStack extends RoboBrowser {
  protected val browserStackOptions: BrowserStackOptions
  protected def browserStackURL: URL = BrowserStack.url(browserStackOptions.username, browserStackOptions.automateKey)

  override protected def configureOptions(caps: ChromeOptions): Unit = {
    super.configureOptions(caps)

    val o = browserStackOptions
    def t[V](key: String, value: Option[V]): Option[(String, String)] = value.map(v => key -> v.toString)

    val bs = List(
      t("osVersion", options.device.osVersion),
      t("deviceName", options.device.identifier),
      t("realMobile", options.device.realMobile),
      t("projectName", Some(o.projectName)),
      t("buildName", Some(o.buildName)),
      t("sessionName", o.sessionName.orElse(options.device.identifier)),
      t("local", Some(o.local)),
      t("networkLogs", Some(o.networkLogs)),
      t("idleTimeout", Some(o.idleTimeout)),
      t("appiumVersion", Some(o.appiumVersion))
    ).flatten.toMap.asJava

    caps.setCapability("bstack:options", bs)
  }

  def markAsync(status: BrowserStack.Status)(implicit ec: ExecutionContext): Future[Value] =
    BrowserStack.mark(sessionId, browserStackOptions.username, browserStackOptions.automateKey, status)

  def mark(status: BrowserStack.Status): Value = {
    val future = markAsync(status)(scribe.Execution.global)
    Await.result(future, 30.seconds)
  }
}

object BrowserStack {
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

  private def toJson(response: HttpResponse): Value = {
    if (response.status != HttpStatus.OK) throw new RuntimeException(s"Error: ${response.status} - ${response.content.map(_.asString)}")
    val content = response.content.getOrElse(
      throw new RuntimeException(s"No content for request: ${response.status}")
    )
    Json.parse(content.asString)
  }

  def status(sessionId: String, username: String, automateKey: String)
            (implicit ec: ExecutionContext): Future[Value] = client(sessionId, username, automateKey)
    .get
    .send()
    .map(toJson)

  def mark(sessionId: String, username: String, automateKey: String, status: Status)
          (implicit ec: ExecutionContext): Future[Value] = {
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