package robobrowser

import fabric.io.JsonFormatter
import fabric.obj
import fabric.rw.{Asable, Convertible}
import rapid.{Forge, Opt, Task}
import reactify.{Val, Var}
import robobrowser.dom.DOM
import robobrowser.event.ExecutionContext
import robobrowser.input.KeyFeatures
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

  event.runtime.executionContextDestroyed.attach { evt =>
    _executionContexts @= _executionContexts().filterNot(_.id == evt.executionContextId)
  }

  event.runtime.executionContextsCleared.on {
    _executionContexts @= Nil
  }

  lazy val dom: DOM = DOM(this)
  lazy val key: KeyFeatures = KeyFeatures(this)

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

  def windowState(state: WindowState): Task[Unit] = for {
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

  private def closeTarget(targetId: String): Task[Boolean] = send(
    method = "Target.closeTarget",
    params = obj(
      "targetId" -> targetId
    )
  ).map { response =>
    response.result("success").asBoolean
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
  def withBrowser[Return](config: RoboBrowserConfig = RoboBrowserConfig())
                         (forge: Forge[RoboBrowser, Return]): Task[Return] = apply(config).flatMap { browser =>
    forge(browser).guarantee {
      browser.dispose()
    }
  }

  def apply(config: RoboBrowserConfig = RoboBrowserConfig()): Task[RoboBrowser] = for {
    process <- CDP.createProcess(config.browser, config.browserConfig)
    _ <- Task.sleep(500.millis)   // Give the browser time to launch
    tabResults <- CDP.query(config.browser)
    webSocketUrl = tabResults.head.webSocketDebuggerUrl
    webSocket <- CDP.connect(webSocketUrl)
    rb = new RoboBrowser(webSocket, Some(process))
    existingTab = config.tabSelector.select(tabResults)
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
    _ <- rb.enablePage.when(config.enablePageEvents)
    _ <- rb.enableRuntime.when(config.enableRuntime)
    _ <- rb.enableLifecycleEvents.when(config.enableLifecycleEvents)
    _ <- rb.enableDOM.when(config.enableDOMEvents)
    _ <- rb.enableNetworkEvents.when(config.enableNetworkEvents)
  } yield rb
}