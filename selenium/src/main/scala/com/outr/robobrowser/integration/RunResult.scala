package com.outr.robobrowser.integration

sealed trait RunResult {
  def isSuccess: Boolean = this == RunResult.Success
  def isFailure: Boolean = !isSuccess
}

object RunResult {
  case object Success extends RunResult
  case class Failure(test: IntegrationTest, throwable: Throwable) extends RunResult
}