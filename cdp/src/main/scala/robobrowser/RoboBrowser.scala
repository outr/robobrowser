package robobrowser

import fabric.obj
import fabric.rw.Asable
import rapid.{Opt, Task}
import reactify.{Val, Var}
import robobrowser.dom.DOM
import robobrowser.event.ExecutionContext
import robobrowser.select.{Selection, Selector}
import robobrowser.window.{Window, WindowState}
import spice.http.WebSocket

import scala.sys.process._
import scribe.{rapid => logger}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class RoboBrowser private(protected val ws: WebSocket, process: Option[Process]) extends TabFeatures {

  private val _url: Var[String] = Var("about:blank")
  private val _attached: Var[Boolean] = Var(true)

  def url: Val[String] = _url
  def attached: Val[Boolean] = _attached
  def loaded: Val[Boolean] = _loaded

  event.inspector.detached.on {
    _attached @= false
  }

  event.page.frameNavigated.attach { evt =>
    if (evt.frame.id == targetId) {
      _url @= evt.frame.url
      _loaded @= false
    }
  }

  event.page.lifecycle.attach { evt =>
    if (evt.frameId == targetId && evt.name == "load") {
      _loaded @= true
    }
  }

  event.runtime.executionContextCreated.attach { evt =>
    _executionContexts @= evt.context :: _executionContexts()
  }

  event.runtime.executionContextsCleared.on {
    _executionContexts @= Nil
  }

  lazy val dom: DOM = DOM(this)

  def apply(selector: Selector): Selection = Selection(this, selector)

  def enableRuntime: Task[Unit] = send(
    method = "Runtime.enable"
  ).unit

  def enableNetworkEvents: Task[Unit] = send(
    method = "Network.enable"
  ).unit

  def enableDOM: Task[Unit] = send(
    method = "DOM.enable"
  ).unit

  def enablePage: Task[Unit] = send(
    method = "Page.enable"
  ).unit

  def enableLifecycleEvents: Task[Unit] = send(
    method = "Page.setLifecycleEventsEnabled",
    params = obj(
      "enabled" -> true
    )
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

  def waitForCondition(condition: Task[Boolean],
                       cycle: FiniteDuration = 250.millis): Task[Unit] = condition.flatMap {
    case true => Task.unit
    case false => Task.sleep(cycle).flatMap(_ => waitForCondition(condition, cycle))
  }

  def waitForLoaded(cycle: FiniteDuration = 250.millis): Task[Unit] = waitForCondition(Task(loaded()), cycle)

  def waitForDetach(cycle: FiniteDuration = 1.second): Task[Unit] = waitForCondition(Task(!attached()), cycle)

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
            enableRuntime: Boolean = true,
            enablePageEvents: Boolean = true,
            enableLifecycleEvents: Boolean = true,
            enableDOMEvents: Boolean = true,
            enableNetworkEvents: Boolean = true,
            tabSelector: TabSelector = TabSelector.FirstPage): Task[RoboBrowser] = for {
    process <- CDP.createProcess(browser, config)
    _ <- Task.sleep(500.millis)   // Give the browser time to launch
    tabResults <- CDP.query(browser)
    webSocketUrl = tabResults.head.webSocketDebuggerUrl
    webSocket <- CDP.connect(webSocketUrl)
    rb = new RoboBrowser(webSocket, Some(process))
    _ <- rb.enableRuntime.when(enableRuntime)
    _ <- rb.enablePage.when(enablePageEvents)
    _ <- rb.enableLifecycleEvents.when(enableLifecycleEvents)
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