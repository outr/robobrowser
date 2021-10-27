package com.outr.robobrowser.integration

case class AssertionFailed(message: String) extends RuntimeException(message)