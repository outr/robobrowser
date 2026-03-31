package robobrowser

import fabric.io.JsonFormatter
import fabric._
import fabric.dsl._
import fabric.rw._
import rapid.{Forge, Task, logger}
import reactify.{Val, Var}
import robobrowser.dom.DOM
import robobrowser.event.ResponseBody
import robobrowser.input.KeyFeatures
import robobrowser.select.{Selection, Selector}
import robobrowser.window.{Window, WindowState}
import spice.http.WebSocket

import scala.sys.process._
import java.nio.file.{Files, Path}
import java.util.Base64
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
  event.target.detachedFromTarget.attach { evt =>
    if (evt.sessionId == sessionId && evt.targetId == targetId) {
      _attached @= false
    }
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

  def title: Task[String] = Task.next(eval("return document.title").map(_("result")("value").asString))

  def apply(selector: Selector): Selection = Selection(this, selector)

  def enableRuntime: Task[Unit] = send(
    method = "Runtime.enable"
  ).unit

  def enableNetworkEvents: Task[Unit] = send(
    method = "Network.enable",
    params = obj(
      "maxResourceBufferSize" -> 5242880
    )
  ).unit

  def getResponseBody(requestId: String): Task[ResponseBody] = send(
    method = "Network.getResponseBody",
    params = obj(
      "requestId" -> requestId
    )
  ).map(_.result.as[ResponseBody])

  def enableDOM: Task[Unit] = send(
    method = "DOM.enable"
  ).unit

  def enablePage: Task[Unit] = send(
    method = "Page.enable"
  ).unit

  def configureDownloadPath(path: Path): Task[Unit] = send(
    method = "Page.setDownloadBehavior",
    params = obj(
      "behavior" -> "allowAndName",
      "downloadPath" -> path.toAbsolutePath.normalize().toString,
      "eventsEnabled" -> true
    )
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

  def screenshot(file: Path): Task[Unit] = screenshotAs(file)

  /** Set the browser viewport size via CDP device metrics emulation. */
  def setViewportSize(width: Int, height: Int, deviceScaleFactor: Double = 1.0): Task[Unit] = send(
    method = "Emulation.setDeviceMetricsOverride",
    params = obj(
      "width" -> width,
      "height" -> height,
      "deviceScaleFactor" -> deviceScaleFactor,
      "mobile" -> false
    )
  ).unit

  /** Clear device metrics override, restoring default viewport. */
  def clearViewportOverride(): Task[Unit] = send(
    method = "Emulation.clearDeviceMetricsOverride"
  ).unit

  /** Capture a screenshot with options.
    * @param file            output file path
    * @param format          image format: "png" (default) or "jpeg"
    * @param quality         JPEG quality 0-100 (ignored for PNG)
    * @param afterLoadDelay  extra time to wait after page load before capturing (for dynamic JS content)
    *
    * To control the viewport/image size, use `BrowserConfig.windowSize` when creating the browser,
    * or call `setViewportSize()` before taking the screenshot.
    */
  def screenshotAs(file: Path,
                   format: String = "png",
                   quality: Option[Int] = None,
                   afterLoadDelay: Option[FiniteDuration] = None): Task[Unit] = {
    val delayTask = afterLoadDelay match {
      case Some(d) => Task.sleep(d)
      case None => Task.unit
    }
    delayTask.flatMap { _ =>
      val params = quality match {
        case Some(q) => obj("format" -> format, "quality" -> q)
        case None => obj("format" -> format)
      }
      send(method = "Page.captureScreenshot", params = params).map { response =>
        val base64 = response.result("data").asString
        val bytes = Base64.getDecoder.decode(base64)
        Files.write(file, bytes)
      }
    }
  }

  /**
   * Sends JavaScript to change document.querySelector and document.querySelectorAll to search the visible DOM as well
   * as all shadow DOMs until it finds a result. Useful for sites that utilize shadow DOMs like
   */
  def shadowDOMFix(): Task[Unit] = eval(
    """    // Prevent multiple patches
      |    if (document.querySelector._patched) {
      |        console.log("⚠️ Shadow DOM fix already applied. Skipping...");
      |        return;
      |    }
      |
      |    // Preserve original functions
      |    const originalQuerySelector = document.querySelector.bind(document);
      |    const originalQuerySelectorAll = document.querySelectorAll.bind(document);
      |
      |    // Function to collect all shadow roots dynamically
      |    function collectShadowRoots(root = document, shadowRoots = new Set()) {
      |        let shadowRootArray = [];
      |
      |        for (let elem of root.querySelectorAll("*")) {
      |            if (elem.shadowRoot && !shadowRoots.has(elem.shadowRoot)) {
      |                shadowRoots.add(elem.shadowRoot);
      |                shadowRootArray.push(elem.shadowRoot);
      |                shadowRootArray = shadowRootArray.concat(collectShadowRoots(elem.shadowRoot, shadowRoots));
      |            }
      |        }
      |        return shadowRootArray;
      |    }
      |
      |    // Global storage for shadow roots
      |    let allShadowRoots = collectShadowRoots(document);
      |
      |    // Function to manually refresh shadow roots
      |    window.refreshShadowRoots = function() {
      |        allShadowRoots = collectShadowRoots(document);
      |        console.log(`🔄 Manually refreshed shadow roots: ${allShadowRoots.length} found.`);
      |    };
      |
      |    // Observe changes in the DOM to detect new shadow roots
      |    const observer = new MutationObserver((mutations) => {
      |        let updated = false;
      |
      |        mutations.forEach((mutation) => {
      |            mutation.addedNodes.forEach((node) => {
      |                if (node.nodeType === 1) { // Ensure it's an element
      |                    const newRoots = collectShadowRoots(node);
      |                    if (newRoots.length > 0) {
      |                        allShadowRoots = allShadowRoots.concat(newRoots);
      |                        updated = true;
      |                    }
      |                }
      |            });
      |        });
      |
      |        if (updated) {
      |            console.log(`🔄 Shadow roots updated automatically: ${allShadowRoots.length} found.`);
      |        }
      |    });
      |
      |    observer.observe(document.documentElement, { childList: true, subtree: true });
      |
      |    // New querySelector that works across shadow DOMs
      |    document.querySelector = function(selector) {
      |        let element = originalQuerySelector(selector);
      |        if (element) return element; // Found in Light DOM, return early
      |
      |        for (let shadowRoot of allShadowRoots) { // Now iterable
      |            let el = shadowRoot.querySelector(selector);
      |            if (el) return el; // Return first match
      |        }
      |        return null; // No match found
      |    };
      |
      |    // New querySelectorAll that works across shadow DOMs
      |    document.querySelectorAll = function(selector) {
      |        let elements = [...originalQuerySelectorAll(selector)]; // Start with Light DOM results
      |
      |        for (let shadowRoot of allShadowRoots) { // Now iterable
      |            elements.push(...shadowRoot.querySelectorAll(selector));
      |        }
      |
      |        return elements.length ? elements : document.createDocumentFragment().childNodes;
      |    };
      |
      |    // Mark querySelector as patched to prevent multiple overrides
      |    document.querySelector._patched = true;
      |
      |    console.log("✅ Patched querySelector and querySelectorAll for dynamic shadow DOMs.");
      |""".stripMargin).unit

  def updateShadowDOM(): Task[Unit] = eval("window.refreshShadowRoots()").unit

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
                       cycle: FiniteDuration = 250.millis,
                       timeout: FiniteDuration = 1.hour,
                       start: Long = System.currentTimeMillis()): Task[Boolean] = condition.flatMap {
    case true => Task.pure(true)
    case false =>
      val elapsed = System.currentTimeMillis() - start
      if (elapsed > timeout.toMillis) {
        Task.pure(false)
      } else {
        Task.sleep(cycle).flatMap(_ => waitForCondition(condition, cycle, timeout, start))
      }
  }

  def waitForLoaded(cycle: FiniteDuration = 250.millis,
                    timeout: FiniteDuration = 5.minutes): Task[Boolean] = waitForCondition(Task(loaded()), cycle, timeout)

  def waitForDetach(cycle: FiniteDuration = 1.second): Task[Boolean] = waitForCondition(Task(!attached()), cycle, 7.days)

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
  var NavigateRetries: Int = 3

  def withBrowser[Return](config: RoboBrowserConfig = RoboBrowserConfig())
                         (forge: Forge[RoboBrowser, Return]): Task[Return] = apply(config).flatMap { browser =>
    forge(browser).guarantee {
      browser.dispose()
    }
  }

  def apply(config: RoboBrowserConfig = RoboBrowserConfig()): Task[RoboBrowser] = for {
    browser <- Task(config.browser.resolvePort())
    // Use a unique user data dir per instance to avoid Chrome's SingletonLock conflict
    browserConfig = if (config.browser.port == 0) {
      config.browserConfig.copy(userDataDir = BrowserConfig.resolveDataDir(s"instance-${browser.port}"))
    } else {
      config.browserConfig
    }
    _ <- Task(browserConfig.prepareUserDataDir())
    process <- CDP.createProcess(browser, browserConfig)
    _ <- Task.sleep(500.millis)   // Give the browser time to launch
    tabResults <- CDP.query(browser)
    webSocketUrl = tabResults.head.webSocketDebuggerUrl
    _ <- logger.info(s"Connecting to WebSocket: $webSocketUrl")
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