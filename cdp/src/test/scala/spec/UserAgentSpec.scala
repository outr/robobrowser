package spec

import com.sun.net.httpserver.HttpServer
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import rapid.{AsyncTaskSpec, Task}
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig, UserAgent}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

/** A headless RoboBrowser presents itself as the ordinary desktop browser everywhere a site can look: the User-Agent
  * and client-hint headers it sends, and what scripts in the page and in its workers read from `navigator`. */
class UserAgentSpec extends AsyncWordSpec with AsyncTaskSpec with Matchers {
  private var browser: RoboBrowser = uninitialized
  private var server: HttpServer = uninitialized
  private val requests = new ConcurrentLinkedQueue[Map[String, String]]()

  private val page =
    """<html><body><script>
      |window.workerUa = null;
      |const w = new Worker('/worker.js');
      |w.onmessage = e => window.workerUa = e.data;
      |</script></body></html>""".stripMargin

  private def value(js: String): Task[String] = browser.eval(s"return String($js)").map(_("result")("value").asString)

  private def serve(path: String, contentType: String, body: String): Unit = server.createContext(path, exchange => {
    requests.add(exchange.getRequestHeaders.asScala.map { case (k, v) => k.toLowerCase -> v.asScala.mkString(",") }.toMap)
    val bytes = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.add("Content-Type", contentType)
    exchange.sendResponseHeaders(200, bytes.length)
    exchange.getResponseBody.write(bytes)
    exchange.close()
  })

  "UserAgent" should {
    "turn a headless User-Agent into the ordinary one" in {
      UserAgent.unheadless("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36") shouldBe
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36"
    }
    "list the brands real Chrome lists" in {
      val ua = UserAgent.ordinary(154)
      val meta = UserAgent.metadata(ua, Some(UserAgent.Product("Google Chrome", "154.0.8037.92"))).get
      meta("brands").asVector.map(_("brand").asString).toList shouldBe List("Not)A;Brand", "Chromium", "Google Chrome")
      meta("fullVersion").asString shouldBe "154.0.8037.92"
      UserAgent.metadata("curl/8.0", None) shouldBe None
    }
  }

  "A headless RoboBrowser" should {
    "load a page that starts a worker" in {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
      serve("/worker.js", "text/javascript", "postMessage(navigator.userAgent);")
      serve("/", "text/html", page)
      server.start()
      RoboBrowser(RoboBrowserConfig(browserConfig = BrowserConfig(noSandbox = true, disableDevSHMUsage = true, disableGPU = true)))
        .flatMap { b =>
          browser = b
          b.navigate(s"http://localhost:${server.getAddress.getPort}/")
        }
        .flatMap(_ => browser.waitForLoaded())
        .flatMap(_ => browser.waitForCondition(value("window.workerUa !== null").map(_ == "true"), timeout = scala.concurrent.duration.DurationInt(10).seconds))
        .map(_ shouldBe true)
    }
    "send the ordinary User-Agent and client hints" in Task {
      val page = requests.asScala.find(_.contains("user-agent")).get
      page("user-agent") should (include("Chrome/") and not include "Headless")
      page("sec-ch-ua") should (include("Chromium") and not include "Headless")
    }
    "read as the ordinary browser from the page" in {
      for {
        ua <- value("navigator.userAgent")
        brands <- value("navigator.userAgentData.brands.map(b => b.brand).join(',')")
        webdriver <- value("navigator.webdriver")
      } yield {
        ua should (include("Chrome/") and not include "Headless")
        brands should (include("Chromium") and not include "Headless")
        webdriver shouldBe "false"
      }
    }
    "read as the ordinary browser from a worker" in {
      value("window.workerUa").map(_ should (include("Chrome/") and not include "Headless"))
    }
    "dispose" in {
      browser.dispose().map { _ =>
        server.stop(0)
        succeed
      }
    }
  }
}
