package com.outr.robobrowser.logging

import com.outr.robobrowser.RoboBrowser

trait LoggingSupport extends RoboBrowser {
  def logs: LoggingImplementation
}