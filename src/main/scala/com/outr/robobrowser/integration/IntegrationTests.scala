package com.outr.robobrowser.integration

import com.outr.robobrowser.{BrowserStack, RoboBrowser}
import scribe.data.MDC

import java.io.File
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Try

trait IntegrationTests[Browser <: RoboBrowser] { suite =>
  private var _tests = List.empty[IntegrationTest]

  /**
   * If true, automatically calls browser.dispose() after test run is complete (defaults to true)
   */
  protected def autoDispose: Boolean = true

  /**
   * If true, when a test fails, an image, log, and HTML are logged to the filesystem
   */
  protected def debug: Boolean = true

  def label: String

  def browser: Browser

  def log: Boolean = true

  def tests: List[IntegrationTest] = _tests

  private val context = new ThreadLocal[Option[String]] {
    override def initialValue(): Option[String] = None
  }

  implicit class StringTest(description: String) {
    def when(f: => Unit): Unit = {
      val previous = context.get()
      context.set(Some(previous.map(p => s"$p $description").getOrElse(description)))
      try {
        f
      } finally {
        context.set(previous)
      }
    }
    def in(f: => Any)(implicit pkg: sourcecode.Pkg,
                      fileName: sourcecode.FileName,
                      name: sourcecode.Name,
                      line: sourcecode.Line): Unit = suite.synchronized {
      val index = _tests.length
      val c = context.get()
      _tests = _tests ::: List(IntegrationTest(index, description, c, () => f, pkg, fileName, name, line))
    }
  }

  def finish(label: String, result: RunResult): Unit = {
    if (browser.isBrowserStack) {
      result match {
        case RunResult.Success =>
          browser.mark(BrowserStack.Status.Passed(s"$label successfully passed"))
        case RunResult.Failure(test, throwable) =>
          val description = test.context.map(c => s"$c: ${test.description}").getOrElse(test.description)
          browser.mark(BrowserStack.Status.Failed(s"$description failed with message: ${throwable.getMessage}"))
      }
    }

    if (result.isFailure && debug) {
      Try {
        val dir = new File("debug")
        dir.mkdirs()
        browser.debug(dir, label)
      }.failed.foreach { t =>
        scribe.warn(s"Error while attempting to write debug information: ${t.getMessage}")
      }
    }
  }

  def run(): RunResult = {
    scribe.elapsed {
      if (log) scribe.info(s"$label should:")

      def recurse(tests: List[IntegrationTest], previousContext: Option[String]): RunResult = if (tests.isEmpty) {
        RunResult.Success
      } else {
        val test = tests.head
        if (log && test.context != previousContext) {
          test.context.foreach { context =>
            scribe.info(s"- $context:")
          }
        }
        val padding = if (test.context.isEmpty) "" else "  "
        if (log) {
          scribe.info(s"$padding- ${test.description}...")
        }
        val start = System.nanoTime()
        try {
          MDC.contextualize("test", s"$label should ${test.description}") {
            test.function() match {
              case f: Future[_] => Await.result(f, Duration.Inf)
              case _ => // Ignore everything else
            }
          }
          if (log) {
            val elapsed = System.nanoTime() - start
            val millis = TimeUnit.NANOSECONDS.toMillis(elapsed)
            scribe.info(s"$padding  - Success in ${millis / 1000.0} seconds")
          }
          recurse(tests.tail, test.context)
        } catch {
          case t: Throwable =>
            if (log) {
              val elapsed = System.nanoTime() - start
              val millis = TimeUnit.NANOSECONDS.toMillis(elapsed)
              scribe.error(s"$padding  - Failure in ${millis / 1000.0} seconds")
            }
            RunResult.Failure(test, t)
        }
      }

      val result = recurse(tests, None)
      try {
        def traceInfo(test: IntegrationTest): String = {
          val (_, className) = scribe.LoggerSupport.className(test.pkg, test.fileName)
          val methodName = test.name.value match {
            case "anonymous" | "" => None
            case v => Option(v)
          }
          val location = methodName match {
            case Some(n) => s"$className.$n"
            case None => className
          }
          s"$location:${test.line.value}"
        }

        result match {
          case RunResult.Success => scribe.info(s"$label completed successfully")
          case RunResult.Failure(test, throwable) => throwable match {
            case AssertionFailed(message) =>
              scribe.error(s"$label failed on: ${test.description} (test: ${test.index + 1}, location: ${traceInfo(test)}) - $message")
            case _ => scribe.error(s"$label failed on: ${test.description} (test: ${test.index + 1}, location: ${traceInfo(test)})", throwable)
          }
        }
        result
      } finally {
        Try(finish(label, result)).failed.foreach { throwable =>
          scribe.error(s"Error occurred while attempting to 'finish'", throwable)
        }
        if (autoDispose) {
          browser.dispose() // Make sure the browser was disposed
        }
      }
    }
  }
}
