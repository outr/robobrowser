package com.outr.robobrowser.integration

import com.outr.robobrowser.RoboBrowser
import profig._

import scala.annotation.tailrec

trait IntegrationTestSuite {
  private var scenarios = List.empty[IntegrationTestsInstance[_ <: RoboBrowser]]

  def stopOnAnyFailure: Boolean = false

  def testWith[Browser <: RoboBrowser](f: => IntegrationTests[Browser]): Unit = synchronized {
    scenarios = scenarios ::: List(IntegrationTestsInstance(() => f))
  }

  def main(args: Array[String]): Unit = {
    Profig.initConfiguration()
    Profig.merge(args.toList)
    scribe.info(s"Starting execution of ${scenarios.length} scenarios...")
    // TODO: Support multi-threading
    val start = System.currentTimeMillis()
    val failures = recurse(scenarios, Nil)
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
                      failures: List[RunResult.Failure]): List[RunResult.Failure] = if (scenarios.isEmpty) {
    failures.reverse
  } else {
    val instance = scenarios.head.create()
    instance.run() match {
      case failure: RunResult.Failure if stopOnAnyFailure => (failure :: failures).reverse
      case result =>
        val updated = result match {
          case failure: RunResult.Failure => failure :: failures
          case _ => failures
        }
        recurse(scenarios.tail, updated)
    }
  }
}