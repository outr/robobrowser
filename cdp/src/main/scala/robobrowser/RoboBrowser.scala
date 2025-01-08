package robobrowser

import fabric.obj
import fabric.rw.Asable
import rapid.Task
import robobrowser.window.{Window, WindowState}
import spice.http.WebSocket

import scala.sys.process._
import scribe.{rapid => logger}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class RoboBrowser private(protected val ws: WebSocket, process: Option[Process]) extends TabFeatures {
  private var attached = true

  event.inspector.detached.on {
    attached = false
  }

  event.page.navigatedWithinDocumentEvent.attach { evt =>
    scribe.info(s"Navigated: ${evt.frameId} / ${evt.navigationType} / ${evt.url}")
  }

  def enableNetworkEvents: Task[Unit] = send(
    method = "Network.enable"
  ).unit

  def enableDOM: Task[Unit] = send(
    method = "DOM.enable"
  ).unit

  def enablePage: Task[Unit] = send(
    method = "Page.enable"
  ).unit

  def window: Task[Window] = send(
    method = "Browser.getWindowForTarget"
  ).map { response =>
    response.result.as[Window]
  }

  def windowState_=(state: WindowState): Task[Unit] = for {
    window <- this.window
    _ <- send(
      method = "Browser.setWindowBounds",
      params = obj(
        "windowId" -> window.windowId,
        "bounds" -> obj(
          "windowState" -> state.name
        )
      )
    )
  } yield ()

  def waitForDetach(cycle: FiniteDuration = 1.second): Task[Unit] = if (!attached) {
    Task.unit
  } else {
    Task.sleep(cycle).flatMap(_ => waitForDetach(cycle))
  }

  private def createTarget(url: String): Task[String] = send(
    method = "Target.createTarget",
    params = obj(
      "url" -> url
    )
  ).map { response =>
    response.result("targetId").asString
  }

  private def attachToTarget(targetId: String): Task[String] = send(
    method = "Target.attachToTarget",
    params = obj(
      "targetId" -> targetId,
      "flatten" -> true
    )
  ).map { response =>
    response.result("sessionId").asString
  }

  def disconnect(): Task[Unit] = Task(ws.disconnect())

  def dispose(): Task[Unit] = disconnect().map { _ =>
    process.foreach { p =>
      p.destroy()
    }
  }
}

object RoboBrowser {
  def apply(browser: Browser = Browser.Chrome,
            config: BrowserConfig = BrowserConfig(),
            enablePageEvents: Boolean = true,
            enableDOMEvents: Boolean = true,
            enableNetworkEvents: Boolean = true,
            tabSelector: TabSelector = TabSelector.FirstPage): Task[RoboBrowser] = for {
    process <- CDP.createProcess(browser, config)
    _ <- Task.sleep(500.millis)   // Give the browser time to launch
    tabResults <- CDP.query(browser)
    webSocketUrl = tabResults.head.webSocketDebuggerUrl
    webSocket <- CDP.connect(webSocketUrl)
    rb = new RoboBrowser(webSocket, Some(process))
    _ <- rb.enablePage.when(enablePageEvents)
    _ <- rb.enableDOM.when(enableDOMEvents)
    _ <- rb.enableNetworkEvents.when(enableNetworkEvents)
    existingTab = tabSelector.select(tabResults)
    _ <- existingTab match {
      case Some(tab) => rb.attachToTarget(tab.id).map { sessionId =>
        rb.targetId = tab.id
        rb.sessionId = sessionId
      }
      case None => for {
        targetId <- rb.createTarget("about:blank")
        sessionId <- rb.attachToTarget(targetId)
      } yield {
        rb.targetId = targetId
        rb.sessionId = sessionId
      }
    }
  } yield rb
}