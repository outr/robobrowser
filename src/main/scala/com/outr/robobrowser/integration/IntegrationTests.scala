package com.outr.robobrowser.integration

import com.outr.robobrowser.RoboBrowser
import scribe.Logger
import scribe.data.MDC
import scribe.format._

import java.util.concurrent.TimeUnit

trait IntegrationTests[Browser <: RoboBrowser] { suite =>
  private var _tests = List.empty[IntegrationTest]

  protected val logger: Logger = Logger.empty
    .orphan()
    .withHandler(formatter = formatter"${green(dateFull)} ${string("[")}$levelColoredPaddedRight${string("]")}: $message$mdc")

  def label: String

  def browser: Browser

  def log: Boolean = true

  def tests: List[IntegrationTest] = _tests

  implicit class StringTest(description: String) {
    def in(f: => Unit): Unit = suite.synchronized {
      val index = _tests.length
      _tests = _tests ::: List(IntegrationTest(index, description, () => f))
    }
  }

  def finish(label: String, result: RunResult): Unit = {}

  implicit class Assertions[T](value: T) {
    def should(comparison: Comparison[T]): Unit = comparison.compareWith(value)
  }

  def be[T](expected: T): Comparison[T] = EqualityComparison(expected)

  def run(): RunResult = {
    logger.elapsed {
      if (log) logger.info(s"$label should:")

      def recurse(tests: List[IntegrationTest]): RunResult = if (tests.isEmpty) {
        RunResult.Success
      } else {
        val test = tests.head
        if (log) logger.info(s"\t${test.description}...")
        val start = System.nanoTime()
        try {
          MDC.contextualize("test", s"$label should ${test.description}") {
            test.function()
          }
          if (log) {
            val elapsed = System.nanoTime() - start
            val millis = TimeUnit.NANOSECONDS.toMillis(elapsed)
            logger.info(s"\t\tSuccess in ${millis / 1000.0} seconds")
          }
          recurse(tests.tail)
        } catch {
          case t: Throwable =>
            if (log) {
              val elapsed = System.nanoTime() - start
              val millis = TimeUnit.NANOSECONDS.toMillis(elapsed)
              logger.info(s"\t\tFailure in ${millis / 1000.0} seconds")
            }
            RunResult.Failure(test, t)
        }
      }

      val result = recurse(tests)
      finish(label, result)
      browser.dispose() // Make sure the browser was disposed
      result match {
        case RunResult.Success => logger.info(s"$label completed successfully")
        case RunResult.Failure(test, throwable) => logger.error(s"$label failed on ${test.description} (${test.index})", throwable)
      }
      result
    }
  }
}
