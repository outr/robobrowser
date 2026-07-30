package spec

import rapid.{Task, logger}
import robobrowser.fetch.{RequestPattern, RequestStage}
import robobrowser.{BrowserApp, RoboBrowser}
import spice.http.{Headers, HttpStatus}

object Test3 extends BrowserApp {
  override def run(browser: RoboBrowser): Task[Unit] = for {
    _ <- logger.info("Loading...")
    _ <- init(browser)
    _ <- browser.fetch.enable(List(RequestPattern(requestStage = Some(RequestStage.Response))))
    _ = browser.event.fetch.requestPaused.attach { evt =>
      scribe.info(s"Paused: ${evt.request.url} / ${evt.headers.response.first(Headers.`Content-Type`)}")
//      browser.fetch.continueRequest(evt.requestId).start()
      browser.fetch.fulfillRequest(evt.requestId, HttpStatus.OK.code).start()
    }
    _ <- browser.navigate("https://www.enovationcontrols.com/wp-content/uploads/2025/10/OpenView-S70-Data-Sheet.pdf")
    _ <- logger.info("Complete!")
  } yield ()

  private def init(browser: RoboBrowser): Task[Unit] = Task {
    RoboBrowser.NavigateRetries = 0
    browser.event.network.responseReceived.attach { evt =>
      scribe.info(s"*** ResponseReceived: ${evt.response.url} (${evt.requestId})")
    }
    browser.event.network.loadingFinished.attach { evt =>
      scribe.info(s"*** LoadingFinished: ${evt.requestId}")
    }
    browser.event.network.loadingFailed.attach { evt =>
      scribe.info(s"*** Loading Failed: ${evt.requestId}")
    }
    browser.event.page.downloadWillBegin.attach { evt =>
      scribe.info(s"*** Download Will Begin: ${evt.suggestedFilename} / ${evt.url} / ${evt.guid}")
    }
    browser.event.page.downloadProgress.attach { evt =>
      scribe.info(s"*** Download Progress: ${evt.guid} / ${evt.state} / ${evt.receivedBytes} of ${evt.totalBytes} / ${evt.guid}")
    }
  }
}
