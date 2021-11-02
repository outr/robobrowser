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

trait BrowserStack extends RoboBrowser {
  protected def browserStackConsole: String = "errors"
  protected def browserStackNetworkLogs: Boolean = true
  protected def browserStackName: String = "RoboBrowser Test"
  protected def browserStackBuild: String = "Default Build"
  protected def browserStackAppiumVersion: String = "1.21.0"
  protected def browserStackIdleTimeout: String = "300"
  protected def browserStackUsername: String
  protected def browserStackAutomateKey: String

  protected def browserStackURL: URL = BrowserStack.url(browserStackUsername, browserStackAutomateKey)

  override protected def configureOptions(options: ChromeOptions): Unit = {
    super.configureOptions(options)

    options.setCapability("browserstack.console", browserStackConsole)
    options.setCapability("browserstack.networkLogs", browserStackNetworkLogs.toString)
    options.setCapability("name", browserStackName)
    options.setCapability("build", browserStackBuild)
    options.setCapability("browserstack.appium_version", browserStackAppiumVersion)
    options.setCapability("browserstack.idleTimeout", browserStackIdleTimeout)
  }

  def markAsync(status: BrowserStack.Status)(implicit ec: ExecutionContext): Future[Value] =
    BrowserStack.mark(sessionId, browserStackUsername, browserStackAutomateKey, status)

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