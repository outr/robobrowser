package com.outr.robobrowser.integration

import com.outr.robobrowser.RoboBrowser
import scribe.data.MDC

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

trait IntegrationTests[Browser <: RoboBrowser] { suite =>
  private var _tests = List.empty[IntegrationTest]

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
    def in(f: => Any): Unit = suite.synchronized {
      val index = _tests.length
      val d = context.get() match {
        case Some(c) => s"$c when $description"
        case None => description
      }
      _tests = _tests ::: List(IntegrationTest(index, d, () => f))
    }
  }

  def finish(label: String, result: RunResult): Unit = {}

  implicit class Assertions[T](value: T) {
    def should(comparison: Comparison[T]): Unit = comparison.compareWith(value)
    def shouldNot(comparison: Comparison[T]): Unit = comparison.compareNot(value)
  }

  object be {
    def apply[T](expected: T): Comparison[T] = EqualityComparison(expected)
    def <[T : Ordering](expected: T): Comparison[T] = {
      val ordering = implicitly[Ordering[T]]
      BooleanComparison[T](
        f = value => ordering.lt(value, expected),
        failMessage = value => s"$value was not < $expected",
        failNotMessage = value => s"$value was < $expected"
      )
    }
    def >[T : Ordering](expected: T): Comparison[T] = {
      val ordering = implicitly[Ordering[T]]
      BooleanComparison[T](
        f = value => ordering.gt(value, expected),
        failMessage = value => s"$value was not > $expected",
        failNotMessage = value => s"$value was > $expected"
      )
    }
  }

//  def be[T](expected: T): Comparison[T] = EqualityComparison(expected)

  def run(): RunResult = {
    scribe.elapsed {
      if (log) scribe.info(s"$label should:")

      def recurse(tests: List[IntegrationTest]): RunResult = if (tests.isEmpty) {
        RunResult.Success
      } else {
        val test = tests.head
        if (log) scribe.info(s"- ${test.description}...")
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
            scribe.info(s"  - Success in ${millis / 1000.0} seconds")
          }
          recurse(tests.tail)
        } catch {
          case t: Throwable =>
            if (log) {
              val elapsed = System.nanoTime() - start
              val millis = TimeUnit.NANOSECONDS.toMillis(elapsed)
              scribe.error(s"  - Failure in ${millis / 1000.0} seconds")
            }
            RunResult.Failure(test, t)
        }
      }

      val result = recurse(tests)
      finish(label, result)
      browser.dispose() // Make sure the browser was disposed
      result match {
        case RunResult.Success => scribe.info(s"$label completed successfully")
        case RunResult.Failure(test, throwable) => throwable match {
          case AssertionFailed(message) =>
            scribe.error(s"$label failed on: ${test.description} (${test.index}) - $message")
          case _ => scribe.error(s"$label failed on: ${test.description} (${test.index})", throwable)
        }
      }
      result
    }
  }
}
