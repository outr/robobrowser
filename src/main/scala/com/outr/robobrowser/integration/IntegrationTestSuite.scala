package com.outr.robobrowser.integration

import com.outr.robobrowser.{BrowserConsoleWriter, RoboBrowser}
import profig._
import scribe._
import scribe.format._
import scribe.output.format.ASCIIOutputFormat

import scala.annotation.tailrec

trait IntegrationTestSuite {
  private var _browser: Option[RoboBrowser] = None
  private var scenarios = List.empty[IntegrationTestsInstance[_ <: RoboBrowser]]

  def stopOnAnyFailure: Boolean = false
  def logToConsole: Boolean = true
  def retries: Int = 0

  object test {
    def on[Browser <: RoboBrowser](f: => IntegrationTests[Browser]): Unit = synchronized {
      scenarios = scenarios ::: List(IntegrationTestsInstance(() => f))
    }
  }

  def main(args: Array[String]): Unit = {
    Profig.initConfiguration()
    Profig.merge(args.toList)

    Logger.root
      .clearHandlers()
      .withHandler(formatter = Formatter.colored)
      .replace()

    if (logToConsole) {
      val writer = BrowserConsoleWriter(() => _browser)
//      val outputFormat = RichBrowserOutputFormat(writer)
      val outputFormat = ASCIIOutputFormat
      Logger.root.withHandler(
        writer = writer,
        outputFormat = outputFormat
      ).replace()
    }

    scribe.info(s"Starting execution of ${scenarios.length} scenarios...")
    // TODO: Support multi-threading
    val start = System.currentTimeMillis()
    val failures = recurse(scenarios, Nil, 0)
    val elapsed = (System.currentTimeMillis() - start) / 1000.0
    if (failures.isEmpty) {
      scribe.info(s"Successful execution of ${scenarios.length} scenarios in $elapsed seconds.")
      sys.exit(0)
    } else {
      scribe.error(s"${failures.length} failed of ${scenarios.length} scenarios in $elapsed seconds.")
      sys.exit(1)
    }
  }

  @tailrec
  private def recurse(scenarios: List[IntegrationTestsInstance[_ <: RoboBrowser]],
                      failures: List[RunResult.Failure],
                      failed: Int): List[RunResult.Failure] = if (scenarios.isEmpty) {
    _browser = None
    failures.reverse
  } else {
    val instance = scenarios.head.create()
    _browser = Some(instance.browser)
    instance.run() match {
      case failure: RunResult.Failure if stopOnAnyFailure && failed >= retries => (failure :: failures).reverse
      case result =>
        val (updated, fails) = result match {
          case _: RunResult.Failure if failed < retries => (failures, failed + 1)
          case failure: RunResult.Failure => (failure :: failures, 0)
          case _ => (failures, 0)
        }
        val list = if (fails > 0) scenarios else scenarios.tail
        recurse(list, updated, fails)
    }
  }
}