package com.outr.robobrowser.integration

case class IntegrationTest(index: Int, description: String, function: () => Unit)