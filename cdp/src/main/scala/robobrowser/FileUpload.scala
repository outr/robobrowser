package robobrowser

import fabric.{Arr, Bool, NumInt, Str, obj}
import fabric.io.JsonFormatter
import rapid.Task
import robobrowser.event.FileChooserOpenedEvent

import java.nio.file.Path
import scala.concurrent.duration.{DurationInt, FiniteDuration}

/** Putting files into a page's upload controls. A page's "choose a file" opens a native chooser the browser cannot
  * drive; with the chooser intercepted, the page announces it as [[FileChooserOpenedEvent]] instead, naming the file
  * input that opened it, which is then filled directly. Reached as `browser.files`. */
class FileUpload(browser: RoboBrowser) {

  /** Whether a file chooser the page opens is announced as [[FileChooserOpenedEvent]] instead of shown. */
  def interceptChooser(enabled: Boolean): Task[Unit] =
    browser.send("Page.setInterceptFileChooserDialog", obj("enabled" -> Bool(enabled))).unit

  /** Set the files of the file input `backendNodeId`; the page sees its usual `input` and `change` events. */
  def setInputFiles(backendNodeId: Int, paths: List[Path]): Task[Unit] =
    browser.send("DOM.setFileInputFiles", obj(
      "files" -> Arr(paths.map(p => Str(p.toAbsolutePath.normalize().toString)).toVector),
      "backendNodeId" -> NumInt(backendNodeId.toLong)
    )).unit

  /** Set the files of the file input `selector` matches — hidden or not, no chooser involved. `false`, with nothing
    * set, when the first match is not a file input. */
  def setInputFiles(selector: String, paths: List[Path]): Task[Boolean] = {
    val find = s"(() => { const el = document.querySelector(${JsonFormatter.Compact(Str(selector))}); " +
      "return el instanceof HTMLInputElement && el.type === 'file' ? el : null; })()"
    browser.send("Runtime.evaluate", obj("expression" -> Str(find), "returnByValue" -> Bool(false))).flatMap { response =>
      response.result.get("result").flatMap(_.get("objectId")) match {
        case Some(objectId) =>
          browser.send("DOM.setFileInputFiles", obj(
            "files" -> Arr(paths.map(p => Str(p.toAbsolutePath.normalize().toString)).toVector),
            "objectId" -> objectId
          )).map(_ => true)
        case None => Task.pure(false)
      }
    }
  }

  /** Upload `paths` through whatever file chooser `trigger` opens — the click on the page's "attach" button, by
    * selector or by coordinates. Fails when no chooser opens within `timeout`, when the chooser is not a file input,
    * or when it takes one file and more are given. */
  def upload(paths: List[Path], timeout: FiniteDuration = 10.seconds)(trigger: Task[Unit]): Task[FileChooserOpenedEvent] = {
    val opened = Task.completable[FileChooserOpenedEvent]
    val listener = browser.event.page.fileChooserOpened.attach(e => if (opened.result.isEmpty) opened.success(e))
    val run = for {
      _ <- interceptChooser(enabled = true)
      _ <- trigger
      chooser <- opened.timeout(timeout).handleError(_ =>
        Task.error(new RuntimeException(s"No file chooser opened within $timeout of the trigger")))
      node <- chooser.backendNodeId match {
        case Some(id) => Task.pure(id)
        case None => Task.error(new RuntimeException("The page's file chooser is not a file input, so it cannot be filled"))
      }
      _ <- Task.error(new RuntimeException(s"The file chooser takes one file; ${paths.size} were given"))
        .when(chooser.mode == "selectSingle" && paths.size > 1)
      _ <- setInputFiles(node, paths)
    } yield chooser
    run.guarantee(Task {
      browser.event.page.fileChooserOpened.reactions -= listener
    }.flatMap(_ => interceptChooser(enabled = false).handleError(_ => Task.unit)))
  }
}
