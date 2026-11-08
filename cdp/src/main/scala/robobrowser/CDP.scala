package robobrowser

import fabric.Json
import fabric.io.JsonFormatter
import rapid.Task
import robobrowser.comm.CDPQueryResult
import spice.UserException
import spice.http.{ConnectionStatus, WebSocket}
import spice.http.client.{HttpClient, RetryManager}
import spice.net.URL

import scala.concurrent.duration.DurationInt
import scala.sys.process._
import fabric.rw._

object CDP {
  def createProcess(browser: Browser, config: BrowserConfig): Task[Process] = BrowserLauncher.createProcess(browser, config)

  def query(browser: Browser = Browser.Chrome): Task[List[CDPQueryResult]] = HttpClient
    .retryManager(RetryManager.simple(20, 1.second))
    .url(URL.parse(s"http://localhost:${browser.port}/json"))
    .call[List[CDPQueryResult]]

  def connect(url: URL): Task[WebSocket] = Task {
    HttpClient
      .url(url)
      .webSocket()
  }.flatTap(_.connect().map {
    case ConnectionStatus.Open => // Success
    case status => throw UserException(s"Error connecting to WebSocket ($url): $status")
  })
}