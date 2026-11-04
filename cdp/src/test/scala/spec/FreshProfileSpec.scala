package spec

import com.sun.net.httpserver.HttpServer
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import rapid.{AsyncTaskSpec, Task}
import robobrowser.{Browser, BrowserConfig, RoboBrowser, RoboBrowserConfig}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*

/** A browser launched on a port an earlier run used gets that run's profile (`instance-<port>`) and with it the
  * earlier run's storage; with `freshProfile` each launch has a new, empty profile, removed when the browser closes or
  * its launch fails, and never used again. */
class FreshProfileSpec extends AsyncWordSpec with AsyncTaskSpec with Matchers {

  private val server: HttpServer = {
    val s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    s.createContext("/", exchange => {
      val body = "<html><body>store</body></html>".getBytes(StandardCharsets.UTF_8)
      exchange.getResponseHeaders.add("Content-Type", "text/html")
      exchange.sendResponseHeaders(200, body.length)
      exchange.getResponseBody.write(body)
      exchange.close()
    })
    s.start()
    s
  }
  private val page = s"http://127.0.0.1:${server.getAddress.getPort}/"

  /** A free port, chosen once: two launches on it land on the same `instance-<port>` profile, as two port-0 runs
    * handed the same port do. */
  private val port: Int = Browser.auto().resolvePort().port

  private def config(fresh: Boolean): RoboBrowserConfig = RoboBrowserConfig(
    browser = Browser.auto().copy(port = port),
    browserConfig = BrowserConfig(userDataDir = BrowserConfig.resolveDataDir(s"instance-$port"), noSandbox = true,
      disableDevSHMUsage = true, disableGPU = true),
    freshProfile = fresh)

  private def tmp: Path = Paths.get(System.getProperty("java.io.tmpdir"))

  private def profiles: Set[Path] = {
    val s = Files.list(tmp)
    try s.iterator().asScala.filter(_.getFileName.toString.startsWith("robobrowser-profile-")).toSet
    finally s.close()
  }

  /** Launch, open the page, read what the page stored before, store a value, close. */
  private def run(fresh: Boolean, value: String): Task[(String, Option[java.io.File])] =
    RoboBrowser(config(fresh)).flatMap { browser =>
      (for {
        _ <- browser.navigate(page)
        before <- browser.eval("return String(localStorage.getItem('mark'))").map(_("result")("value").asString)
        _ <- browser.eval(s"localStorage.setItem('mark', '$value')")
        // A run that ends normally: Chrome closes itself, writing what it held to the profile.
        _ <- browser.send("Browser.close").timeout(5.seconds).attempt
      } yield (before, Option.when(fresh)(browser.userDataDir))).guarantee(browser.dispose()).flatMap { result =>
        Task(awaitUnlocked(browser.userDataDir.toPath)).map(_ => result)
      }
    }

  /** Until no Chrome process runs on the profile any more. */
  private def awaitUnlocked(dir: Path): Unit = {
    val flag = s"--user-data-dir=${dir.toFile.getAbsolutePath}"
    def running: Boolean = ProcessHandle.allProcesses().anyMatch(p => p.info().arguments().map[Boolean](_.contains(flag)).orElse(false))
    val deadline = System.currentTimeMillis() + 15000
    while (running && System.currentTimeMillis() < deadline) Thread.sleep(50)
  }

  "two launches on the same port" should {
    "share the first one's storage by default" in {
      for {
        _ <- run(fresh = false, "first")
        (seen, _) <- run(fresh = false, "second")
      } yield seen shouldBe "first"
    }
    "start empty each time with freshProfile, and leave no profile behind" in {
      val before = profiles
      for {
        _ <- run(fresh = false, "earlier")
        (first, dir1) <- run(fresh = true, "first")
        (second, dir2) <- run(fresh = true, "second")
      } yield {
        first shouldBe "null"
        second shouldBe "null"
        dir1 should not be dir2
        dir1.foreach(_.exists() shouldBe false)
        dir2.foreach(_.exists() shouldBe false)
        (profiles -- before) shouldBe empty
      }
    }
    "remove a fresh profile when the launch fails" in {
      val before = profiles
      RoboBrowser(config(fresh = true).copy(browser = Browser(List("/nonexistent/chrome"), port))).attempt.map { result =>
        result.isFailure shouldBe true
        (profiles -- before) shouldBe empty
      }
    }
  }

  "tear down" should {
    "stop the page server and remove the shared profile" in Task {
      server.stop(0)
      // The default launches' Chrome may still be closing files in it.
      Thread.sleep(2000)
      val dir = BrowserConfig.resolveDataDir(s"instance-$port").toPath
      val s = Files.walk(dir)
      try s.sorted(java.util.Comparator.reverseOrder()).forEach(p => scala.util.Try(Files.deleteIfExists(p)))
      finally s.close()
      succeed
    }
  }
}
