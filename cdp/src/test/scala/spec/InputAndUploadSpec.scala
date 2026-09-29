package spec

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import rapid.{AsyncTaskSpec, Task}
import robobrowser.input.Key
import robobrowser.{BrowserConfig, RoboBrowser, RoboBrowserConfig}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

/**
 * Driving a page with no selectors to lean on — clicks and keys by position and name, and files put into its upload
 * control through the chooser its button opens — plus every protocol event observable whether or not it is modelled.
 */
class InputAndUploadSpec extends AsyncWordSpec with AsyncTaskSpec with Matchers {
  private var browser: RoboBrowser = uninitialized
  private val methods = new ConcurrentLinkedQueue[String]()

  private val page =
    """<html><body style="margin:0">
      |<button id="b" style="position:absolute;left:0;top:0;width:100px;height:40px"
      |  onclick="document.getElementById('f').click()">Attach</button>
      |<textarea id="t" style="position:absolute;left:0;top:60px;width:200px;height:40px"></textarea>
      |<canvas id="c" width="300" height="200" style="position:absolute;left:0;top:120px"></canvas>
      |<input type="file" id="f" multiple style="display:none">
      |<script>
      |window.clicks = []; window.keys = []; window.picked = null;
      |document.getElementById('c').addEventListener('click', e => window.clicks.push(e.offsetX + ',' + e.offsetY + ',' + e.detail));
      |document.addEventListener('keydown', e => window.keys.push(e.key + '/' + e.code));
      |document.getElementById('f').addEventListener('change',
      |  e => window.picked = Array.from(e.target.files).map(f => f.name + ':' + f.size).join(';'));
      |</script></body></html>""".stripMargin

  private def value(js: String): Task[String] = browser.eval(s"return String($js)").map(_("result")("value").asString)

  "A page driven by position and name" should {
    "open" in {
      RoboBrowser(RoboBrowserConfig(browserConfig = BrowserConfig(noSandbox = true, disableDevSHMUsage = true, disableGPU = true)))
        .flatMap { b =>
          browser = b
          b.anyEvent.attach(r => r.method.foreach(methods.add))
          b.navigate("data:text/html," + URLEncoder.encode(page, StandardCharsets.UTF_8).replace("+", "%20"))
        }
        .flatMap(_ => browser.waitForLoaded())
        .map(_ => methods.asScala.toList should contain("Page.loadEventFired"))
    }

    "click a canvas where it is told, once or twice" in {
      for {
        _ <- browser.mouse.click(40, 150)
        _ <- browser.mouse.click(80, 170, clickCount = 2)
        clicks <- value("window.clicks.join('|')")
      } yield clicks shouldBe "40,30,1|80,50,1|80,50,2"
    }

    "type text and press keys by name into the focused field" in {
      for {
        _ <- browser.mouse.click(50, 80)
        _ <- browser.key.insertText("héllo ✓")
        _ <- browser.key.press(Key.named("Enter").get)
        _ <- browser.key.press(Key.named("x").get)
        _ <- browser.key.press(Key.named("ArrowLeft").get)
        text <- value("document.getElementById('t').value")
        keys <- value("window.keys.join('|')")
      } yield {
        text shouldBe "héllo ✓\nx"
        keys shouldBe "Enter/Enter|x/KeyX|ArrowLeft/ArrowLeft"
      }
    }

    "fill the file chooser the page's button opens" in {
      val a = Files.createTempFile("upload-a-", ".txt")
      val b = Files.createTempFile("upload-b-", ".bin")
      Files.writeString(a, "hello")
      Files.write(b, new Array[Byte](2048))
      for {
        chooser <- browser.files.upload(List(a, b))(browser.mouse.click(50, 20))
        picked <- value("window.picked")
      } yield {
        chooser.mode shouldBe "selectMultiple"
        picked shouldBe s"${a.getFileName}:5;${b.getFileName}:2048"
      }
    }

    "say so when the trigger opens no chooser" in {
      browser.files.upload(List(Files.createTempFile("none-", ".txt")), timeout = scala.concurrent.duration.DurationInt(1).second)(
        browser.mouse.click(250, 300)).attempt.map(_.failed.get.getMessage should include("No file chooser opened"))
    }

    "close" in browser.dispose().succeed
  }
}
