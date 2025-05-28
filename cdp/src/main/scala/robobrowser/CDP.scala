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
  def createProcess(browser: Browser, config: BrowserConfig): Task[Process] = Task {
    val cmd = List(
      browser.path,
      s"--remote-debugging-port=${browser.port}",
      "--no-default-browser-check",
      "--no-first-run",
      s"--remote-allow-origins=http://localhost:${browser.port}"
    ) ::: config.options
    scribe.info(cmd.mkString(" "))
    val pb = Process(cmd)
    val logger = ProcessLogger(
      line => scribe.info(line),
      line => scribe.error(line)
    )
    pb.run(logger)
  }

  def query(browser: Browser = Browser.Chrome): Task[List[CDPQueryResult]] = HttpClient
    .retryManager(RetryManager.simple(5, 1.second))
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