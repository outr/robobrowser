package com.outr.robobrowser.integration

import com.outr.robobrowser.{BrowserConsoleWriter, RoboBrowser}
import profig._
import scribe._
import scribe.format._
import scribe.output.format.ASCIIOutputFormat

import scala.annotation.tailrec
import scala.language.implicitConversions

trait IntegrationTestSuite {
  private var _browser: Option[RoboBrowser] = None
  private var scenarios = List.empty[IntegrationTestsInstance[_ <: RoboBrowser]]

  def stopOnAnyFailure: Boolean = false
  def logToConsole: Boolean = false
  def retries: Int = 0

  implicit def f2Instance[Browser <: RoboBrowser](f: => IntegrationTests[Browser]): IntegrationTestsInstance[Browser] =
    IntegrationTestsInstance(() => f)

  object test {
    def on[Browser <: RoboBrowser](instances: IntegrationTestsInstance[Browser]*): Unit = synchronized {
      scenarios = scenarios ::: instances.toList
    }

    def on[Browser <: RoboBrowser](instances: List[IntegrationTestsInstance[Browser]]): Unit = synchronized {
      scenarios = scenarios ::: instances
    }
  }

  def main(args: Array[String]): Unit = {
    Profig.initConfiguration()
    Profig.merge(args.toList)
    val failures = run()
    if (failures.isEmpty) {
      sys.exit(0)
    } else {
      sys.exit(1)
    }
  }

  def run(): List[RunResult.Failure] = {
    Logger.root
      .clearHandlers()
      .withHandler(formatter = Formatter.colored)
      .replace()

  // Add a shutdown hook to make sure we finish
    Runtime.getRuntime.addShutdownHook(new Thread(() => shutdown()))

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
    } else {
      scribe.error(s"${failures.length} failed of ${scenarios.length} scenarios in $elapsed seconds.")
      failures.foreach { f =>
        scribe.error(s"  - ${f.test} failed with ${f.throwable.getMessage}")
      }
    }
    failures
  }

  private var running: Option[IntegrationTests[_ <: RoboBrowser]] = None

  @tailrec
  private def recurse(scenarios: List[IntegrationTestsInstance[_ <: RoboBrowser]],
                      failures: List[RunResult.Failure],
                      failed: Int): List[RunResult.Failure] = if (scenarios.isEmpty) {
    _browser = None
    failures.reverse
  } else {
    val instance = scenarios.head.create()
    _browser = Some(instance.browser)
    running = Some(instance)
    val result = instance.run()
    running = None
    result match {
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

  private def shutdown(): Unit = {
    running.foreach { tests =>
      tests.finish("Cancelled tests", RunResult.Failure(tests.tests.head, new RuntimeException("Cancelled tests")))
    }
  }
}