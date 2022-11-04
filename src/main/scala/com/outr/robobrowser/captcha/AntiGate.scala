//package com.outr.robobrowser.captcha
//
//import cats.effect.IO
//import cats.effect.unsafe.implicits.global
//import com.outr.robobrowser.RoboBrowser
//import com.outr.robobrowser.monitor.BrowserMonitor
//import fabric.Json
//import fabric.rw.RW
//import profig.Profig
//import spice.http.client.HttpClient
//import spice.http.cookie.Cookie
//import spice.net._
//
//import scala.concurrent.duration._
//import scala.util.{Failure, Success}
//
//object AntiGate {
//  def apply(key: String,
//            url: String,
//            template: String,
//            variables: Map[String, String],
//            initialDelay: FiniteDuration = 5.seconds,
//            checkDelay: FiniteDuration = 3.seconds): IO[Either[AntiGateError, AntiGateStatus]] = {
//    create(key, url, template, variables).flatMap {
//      case Left(error) => IO.pure(Left(error))
//      case Right(task) => result(task, initialDelay, checkDelay).map(Right.apply)
//    }
//  }
//
//  def create(key: String, url: String, template: String, variables: Map[String, String]): IO[Either[AntiGateError, AntiGateTask]] = {
//    val request = AntiGateRequest(key, AntiGateTaskDescription("AntiGateTask", url, template, variables))
//    HttpClient
//      .url(url"https://api.anti-captcha.com/createTask")
//      .restful[AntiGateRequest, AntiGateTaskResponse](request)
//      .map {
//        case Success(response) => response.taskId match {
//          case Some(id) => Right(AntiGateTask(id, key))
//          case None => Left(AntiGateError(response.errorId, response.errorCode.getOrElse(""), response.errorDescription.getOrElse("")))
//        }
//        case Failure(exception) => throw exception
//      }
//  }
//
//  def status(task: AntiGateTask): IO[AntiGateStatus] = {
//    val request = AntiGateStatusRequest(task.key, task.id)
//    HttpClient
//      .url(url"https://api.anti-captcha.com/getTaskResult")
//      .restful[AntiGateStatusRequest, AntiGateStatus](request)
//      .map {
//        case Success(s) => s
//        case Failure(exception) => throw exception
//      }
//  }
//
//  def result(task: AntiGateTask,
//             initialDelay: FiniteDuration = 5.seconds,
//             checkDelay: FiniteDuration = 3.seconds): IO[AntiGateStatus] = {
//    val init = IO.sleep(initialDelay)
//    init.flatMap { _ =>
//      status(task).flatMap { status =>
//        if (status.status.contains("processing")) {
//          IO.sleep(checkDelay).flatMap { _ =>
//            result(task, 0.seconds, checkDelay)
//          }
//        } else {
//          IO.pure(status)
//        }
//      }
//    }
//  }
//
//  def main(args: Array[String]): Unit = {
//    Profig.initConfiguration()
//    val key = Profig("antiCaptchaKey").as[String]
//    /*val future = AntiGate(key, "https://www.foodproductdata.com/search?q=028000428433", "Anti-bot screen bypass", Map("css_selector" -> ".cf-browser-verification"))
//    Await.result(future, Duration.Inf) match {
//      case Left(error) => scribe.error(s"An error occurred: $error")
//      case Right(status) =>
//        val json = status.toValue
//        val jsonString = Json.format(json)
//        scribe.info(jsonString)
//    }*/
//
//    val io = test()
//    io.unsafeRunSync()
//
//    sys.exit(0)
//  }
//
//  def test(): IO[Unit] = {
//    val apiKey = Profig("antiCaptchaKey").as[String]
//    lazy val browser = RoboBrowser.Remote
////      .withArguments("proxy-server" -> "--proxy-server=socks5://localhost:1234")
//      .create()
//    val monitor = new BrowserMonitor(browser)
//    browser.load(url"https://www.foodproductdata.com/search?q=028000428433")
////    browser.load(url"https://anti-captcha.com/tutorials/v2-textarea")
//    browser.storage.cookies.add(List(
//      Cookie.Response("cf_chl_seq_ce4bd263c1a304d", "cb316a942c9da76"),
//      Cookie.Response("_jsuid", "1828183838")
//    ))
//    browser.navigate.refresh()
//    monitor.refreshAndPause()
//
////    browser.waitForLoaded(5.seconds)
////    scribe.info(s"Content: ${browser.content()}")
//    IO.unit
//  }
//}
//
//case class AntiGateStatusRequest(clientKey: String, taskId: Long)
//
//object AntiGateStatusRequest {
//  implicit val rw: RW[AntiGateStatusRequest] = RW.gen
//}
//
//case class AntiGateRequest(clientKey: String, task: AntiGateTaskDescription)
//
//object AntiGateRequest {
//  implicit val rw: RW[AntiGateRequest] = RW.gen
//}
//
//case class AntiGateTaskDescription(`type`: String, websiteURL: String, templateName: String, variables: Map[String, String])
//
//object AntiGateTaskDescription {
//  implicit val rw: RW[AntiGateTaskDescription] = RW.gen
//}
//
//case class AntiGateTaskResponse(errorId: Int, taskId: Option[Long], errorCode: Option[String], errorDescription: Option[String])
//
//object AntiGateTaskResponse {
//  implicit val rw: RW[AntiGateTaskResponse] = RW.gen
//}
//
//case class AntiGateTask(id: Long, key: String)
//
//case class AntiGateError(errorId: Int, errorCode: String, errorDescription: String)
//
//case class AntiGateStatus(errorId: Int,
//                          status: Option[String],
//                          errorCode: Option[String],
//                          errorDescription: Option[String],
//                          solution: Option[Json],
//                          cost: Option[String],
//                          ip: Option[String],
//                          createTime: Option[Long],
//                          endTime: Option[Long])
//
//object AntiGateStatus {
//  implicit val rw: RW[AntiGateStatus] = RW.gen
//}