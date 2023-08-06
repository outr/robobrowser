package com.outr.robobrowser.captcha

import cats.effect.IO
import fabric.Json
import fabric.rw._
import spice.http.client.{HttpClient, Proxy}
import spice.net._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object AntiGate {
  def apply(key: String,
            url: URL,
            template: String,
            proxy: Proxy,
            variables: Map[String, String] = Map.empty,
            initialDelay: FiniteDuration = 5.seconds,
            checkDelay: FiniteDuration = 3.seconds): IO[Either[AntiGateError, AntiGateStatus]] = {
    create(key, url, template, proxy, variables).flatMap {
      case Left(error) => IO.pure(Left(error))
      case Right(task) => result(task, proxy, initialDelay, checkDelay).map(Right.apply)
    }
  }

  private def create(key: String,
                     url: URL,
                     template: String,
                     proxy: Proxy,
                     variables: Map[String, String]): IO[Either[AntiGateError, AntiGateTask]] = {
    val request = AntiGateRequest(
      clientKey = key,
      task = AntiGateTaskDescription(
        `type` = "AntiGateTask",
        websiteURL = url,
        templateName = template,
        variables = variables,
        proxyAddress = Some(proxy.host),
        proxyPort = Some(proxy.port),
        proxyLogin = proxy.credentials.map(_.username),
        proxyPassword = proxy.credentials.map(_.password)
      )
    )
    HttpClient
      .url(url"https://api.anti-captcha.com/createTask")
      .proxy(proxy)
      .restfulTry[AntiGateRequest, AntiGateTaskResponse](request)
      .map {
        case Success(response) => response.taskId match {
          case Some(id) => Right(AntiGateTask(id, key))
          case None => Left(AntiGateError(response.errorId, response.errorCode.getOrElse(""), response.errorDescription.getOrElse("")))
        }
        case Failure(exception) => throw exception
      }
  }

  private def status(task: AntiGateTask, proxy: Proxy): IO[AntiGateStatus] = {
    val request = AntiGateStatusRequest(task.key, task.id)
    HttpClient
      .url(url"https://api.anti-captcha.com/getTaskResult")
      .proxy(proxy)
      .restful[AntiGateStatusRequest, AntiGateStatus](request)
  }

  private def result(task: AntiGateTask,
                     proxy: Proxy,
                     initialDelay: FiniteDuration = 5.seconds,
                     checkDelay: FiniteDuration = 3.seconds): IO[AntiGateStatus] = {
    val init = IO.sleep(initialDelay)
    init.flatMap { _ =>
      status(task, proxy).flatMap { status =>
        if (status.status.contains("processing")) {
          IO.sleep(checkDelay).flatMap { _ =>
            result(task, proxy, 0.seconds, checkDelay)
          }
        } else {
          IO.pure(status)
        }
      }
    }
  }

  /*def main(args: Array[String]): Unit = {
    Profig.initConfiguration()
    val key = Profig("antiCaptchaKey").as[String]
    val future = AntiGate(key, "https://www.foodproductdata.com/search?q=028000428433", "Anti-bot screen bypass", Map("css_selector" -> ".cf-browser-verification"))
    Await.result(future, Duration.Inf) match {
      case Left(error) => scribe.error(s"An error occurred: $error")
      case Right(status) =>
        val json = status.toValue
        val jsonString = Json.format(json)
        scribe.info(jsonString)
    }

    val io = test()
    io.unsafeRunSync()

    sys.exit(0)
  }*/

  /*def test(): IO[Unit] = {
    val apiKey = Profig("antiCaptchaKey").as[String]
    lazy val browser = RoboBrowser.Remote
//      .withArguments("proxy-server" -> "--proxy-server=socks5://localhost:1234")
      .create()
    val monitor = new BrowserMonitor(browser)
    browser.load(url"https://www.foodproductdata.com/search?q=028000428433")
//    browser.load(url"https://anti-captcha.com/tutorials/v2-textarea")
    browser.storage.cookies.add(List(
      Cookie.Response("cf_chl_seq_ce4bd263c1a304d", "cb316a942c9da76"),
      Cookie.Response("_jsuid", "1828183838")
    ))
    browser.navigate.refresh()
    monitor.refreshAndPause()

//    browser.waitForLoaded(5.seconds)
//    scribe.info(s"Content: ${browser.content()}")
    IO.unit
  }*/
}

case class AntiGateStatusRequest(clientKey: String, taskId: Long)

object AntiGateStatusRequest {
  implicit val rw: RW[AntiGateStatusRequest] = RW.gen
}

case class AntiGateRequest(clientKey: String, task: AntiGateTaskDescription)

object AntiGateRequest {
  implicit val rw: RW[AntiGateRequest] = RW.gen
}

case class AntiGateTaskDescription(`type`: String,
                                   websiteURL: URL,
                                   templateName: String,
                                   variables: Map[String, String] = Map.empty,
                                   proxyAddress: Option[String] = None,
                                   proxyPort: Option[Int] = None,
                                   proxyLogin: Option[String] = None,
                                   proxyPassword: Option[String] = None)

object AntiGateTaskDescription {
  implicit val rw: RW[AntiGateTaskDescription] = RW.gen
}

case class AntiGateTaskResponse(errorId: Int, taskId: Option[Long], errorCode: Option[String], errorDescription: Option[String])

object AntiGateTaskResponse {
  implicit val rw: RW[AntiGateTaskResponse] = RW.gen
}

case class AntiGateTask(id: Long, key: String)

case class AntiGateError(errorId: Int, errorCode: String, errorDescription: String)

case class AntiGateStatus(errorId: Int,
                          status: Option[String],
                          errorCode: Option[String],
                          errorDescription: Option[String],
                          solution: Option[Json],
                          cost: Option[String],
                          ip: Option[String],
                          createTime: Option[Long],
                          endTime: Option[Long])

object AntiGateStatus {
  implicit val rw: RW[AntiGateStatus] = RW.gen
}