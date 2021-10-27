package com.outr.robobrowser.integration

sealed trait RunResult

object RunResult {
  case object Success extends RunResult
  case class Failure(test: IntegrationTest, throwable: Throwable) extends RunResult
}