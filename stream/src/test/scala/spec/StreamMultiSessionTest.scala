package spec

import rapid._
import robobrowser.display.VirtualDisplayConfig
import robobrowser.stream.Stream.stream
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig, TabSelector}

import scala.concurrent.duration.DurationInt

/** M4 acceptance: two concurrent streamed browsers on separate virtual
  * displays, each with its own viewer. Asserts both connect, both stream
  * distinct content (no frame bleed), and input routed through viewer A only
  * affects browser A (no input bleed).
  * Run: sbt "stream/Test/runMain spec.StreamMultiSessionTest" */
object StreamMultiSessionTest extends RapidApp {
  private def page(label: String): String =
    s"data:text/html,<html><title>$label</title><body style='margin:0;background:%23${if (label == "alpha") "ff0000" else "0000ff"}'>" +
      s"<button id='b' style='position:absolute;left:100px;top:100px;width:200px;height:80px' " +
      s"onclick=\"document.title='$label-clicked'\">$label</button></body></html>"

  private def streamedConfig = RoboBrowserConfig(
    browserConfig = BrowserConfig(noSandbox = true, disableGPU = true),
    tabSelector = TabSelector.FirstPage,
    virtualDisplay = Some(VirtualDisplayConfig(width = 1280, height = 720))
  )

  private def viewerConfig = RoboBrowserConfig(
    browserConfig = BrowserConfig(noSandbox = true, disableGPU = true),
    tabSelector = TabSelector.FirstPage
  )

  override def run(args: List[String]): Task[Unit] =
    RoboBrowser.withBrowser(streamedConfig) { streamedA =>
      RoboBrowser.withBrowser(streamedConfig) { streamedB =>
        val serverA = new StreamDemoServer(streamedA, port = 8899)
        val serverB = new StreamDemoServer(streamedB, port = 8900)
        for {
          _ <- streamedA.navigate(page("alpha"))
          _ <- streamedB.navigate(page("beta"))
          _ <- streamedA.waitForLoaded()
          _ <- streamedB.waitForLoaded()
          _ <- logger.info(s"Displays: A=${streamedA.virtualDisplay.map(_.displayName)} B=${streamedB.virtualDisplay.map(_.displayName)}")
          _ <- serverA.start()
          _ <- serverB.start()
          _ <- RoboBrowser.withBrowser(viewerConfig) { viewerA =>
            RoboBrowser.withBrowser(viewerConfig) { viewerB =>
              for {
                _ <- viewerA.navigate("http://localhost:8899/")
                _ <- viewerB.navigate("http://localhost:8900/")
                _ <- viewerA.waitForLoaded()
                _ <- viewerB.waitForLoaded()
                _ <- waitFor("viewer A connected") {
                  viewerA.eval("return window.viewer ? window.viewer.pc.connectionState : ''")
                    .map(_("result")("value").asString == "connected")
                }
                _ <- waitFor("viewer B connected") {
                  viewerB.eval("return window.viewer ? window.viewer.pc.connectionState : ''")
                    .map(_("result")("value").asString == "connected")
                }
                _ <- waitFor("viewer A frames advancing") {
                  viewerA.eval("return window.viewer.frames").map(_("result")("value").asInt > 10)
                }
                _ <- waitFor("viewer B frames advancing") {
                  viewerB.eval("return window.viewer.frames").map(_("result")("value").asInt > 10)
                }
                // Frame bleed check: sample the center pixel of each video —
                // alpha's page is red, beta's is blue.
                colorA <- centerColor(viewerA)
                colorB <- centerColor(viewerB)
                _ <- logger.info(s"Center colors: A=$colorA B=$colorB")
                _ = require(colorA._1 > 150 && colorA._3 < 100, s"viewer A should see red, saw $colorA")
                _ = require(colorB._3 > 150 && colorB._1 < 100, s"viewer B should see blue, saw $colorB")
                // Input bleed check: click through viewer A only
                _ <- viewerA.eval(
                  """window.viewer.dcSend({type: 'mousedown', x: 200, y: 140, button: 'left', buttons: 1, clickCount: 1, modifiers: 0});
                    |window.viewer.dcSend({type: 'mouseup', x: 200, y: 140, button: 'left', buttons: 0, clickCount: 1, modifiers: 0});
                    |return true;""".stripMargin)
                _ <- waitFor("click routed to browser A") {
                  streamedA.title.map(_ == "alpha-clicked")
                }
                _ <- Task.sleep(1.second)
                titleB <- streamedB.title
                _ = require(titleB == "beta", s"browser B should be untouched, title was '$titleB'")
                _ <- logger.info("M4 PASSED: two concurrent sessions, no frame or input bleed")
              } yield ()
            }
          }
          _ <- serverA.stop()
          _ <- serverB.stop()
        } yield ()
      }
    }.flatMap { _ =>
      // After all browsers are disposed: Netty client event-loop threads are
      // non-daemon and would keep the forked JVM alive after main completes
      Task(System.exit(0))
    }

  private def centerColor(viewer: RoboBrowser): Task[(Int, Int, Int)] = viewer.eval(
    """const v = document.getElementById('v');
      |const c = document.createElement('canvas');
      |c.width = 8; c.height = 8;
      |const ctx = c.getContext('2d');
      |ctx.drawImage(v, v.videoWidth / 2 - 4, v.videoHeight / 2 - 4, 8, 8, 0, 0, 8, 8);
      |const d = ctx.getImageData(4, 4, 1, 1).data;
      |return JSON.stringify([d[0], d[1], d[2]]);""".stripMargin
  ).map { json =>
    val arr = fabric.io.JsonParser(json("result")("value").asString).asVector
    (arr(0).asInt, arr(1).asInt, arr(2).asInt)
  }

  private def waitFor(description: String, timeout: Long = 45000)(check: Task[Boolean]): Task[Unit] = {
    val deadline = System.currentTimeMillis() + timeout
    def loop: Task[Unit] = check.flatMap {
      case true => logger.info(s"M4: $description OK")
      case false if System.currentTimeMillis() > deadline =>
        Task.error(new RuntimeException(s"M4: timed out waiting for $description"))
      case false => Task.sleep(250.millis).flatMap(_ => loop)
    }
    loop
  }
}
