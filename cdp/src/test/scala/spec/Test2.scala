//package spec
//
//import fabric._
//import fabric.dsl._
//import fabric.rw.Convertible
//import rapid._
//import rapid.logger._
//import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}
//import robobrowser.event.HeaderEntry
//import spice.net.URL
//
//import java.util.Locale
//import scala.concurrent.duration._
//import scala.util.{Failure, Success}
//
//object Test2 extends RapidApp {
//  override def run(args: List[String]): Task[Unit] = {
//    val target = args.headOption.getOrElse("https://www.enovationcontrols.com/wp-content/uploads/2025/02/1712047-PV1100-data-sheet.pdf")
//
//    RoboBrowser.withBrowser(
//      config = RoboBrowserConfig(
//        browserConfig = BrowserConfig(
//          headless = true,
//          useNewHeadlessMode = false
//        ),
//        tabSelector = TabSelector.AlwaysCreateNew
//      )
//    ) { browser =>
//      val targetUrl = URL.parse(target, validateTLD = false)
//      val normalizedTarget = targetUrl.toString.toLowerCase(Locale.ROOT)
//
//      @volatile var downloadObserved = false
//
//      def headerValue(headers: List[HeaderEntry], name: String): Option[String] =
//        headers.find(_.name.equalsIgnoreCase(name)).map(_.value)
//
//      browser.event.fetch.requestPaused.attach { event =>
//        val url = event.request.url
//        val isTarget = url.toLowerCase(Locale.ROOT) == normalizedTarget
//        val contentType = headerValue(event.responseHeaders, "content-type")
//        val disposition = headerValue(event.responseHeaders, "content-disposition")
//        val looksLikeDownload = contentType.exists(_.toLowerCase(Locale.ROOT).contains("pdf")) ||
//          disposition.exists(_.nonEmpty)
//        val willDownload = isTarget && looksLikeDownload
//        if (willDownload) {
//          downloadObserved = true
//        }
//        val status = event.responseStatusCode.map(_.toString).getOrElse("pending")
//        scribe.info(
//          s"[FetchInspector] requestId=${event.requestId} url=$url status=$status decision=${if (willDownload) "allowing download" else "continuing"} " +
//            s"contentType=${contentType.getOrElse("<unknown>")} disposition=${disposition.getOrElse("<none>")}"
//        )
//
//        browser.send(
//          method = "Fetch.continueRequest",
//          params = obj("requestId" -> event.requestId.json)
//        ).unit.logErrors.start()
//      }
//
//      val workflow = for {
//        _ <- browser.send(
//          method = "Fetch.enable",
//          params = obj(
//            "patterns" -> arr(
//              obj(
//                "urlPattern" -> "*",
//                "requestStage" -> "Response"
//              )
//            )
//          )
//        ).unit
//        _ <- logger.info(s"[FetchInspector] Navigating to $targetUrl")
//        nav <- browser.navigate(targetUrl.toString).attempt
//        _ <- nav match {
//          case Failure(err) => logger.warn(s"[FetchInspector] Navigation failed: ${err.getMessage}")
//          case Success(_) => Task.unit
//        }
//        _ <- browser.waitForLoaded().attempt
//        _ <- Task.sleep(2.seconds)
//        _ <- if (downloadObserved) {
//          logger.info("[FetchInspector] Download response observed and allowed to continue.")
//        } else {
//          logger.warn("[FetchInspector] No matching download response was observed.")
//        }
//      } yield ()
//
//      val disable = browser
//        .send("Fetch.disable")
//        .unit
//        .attempt
//        .flatMap {
//          case Failure(err) => logger.warn(s"[FetchInspector] Failed to disable Fetch: ${err.getMessage}")
//          case Success(_) => Task.unit
//        }
//
//      workflow.guarantee(disable)
//    }
//  }
//}
//
