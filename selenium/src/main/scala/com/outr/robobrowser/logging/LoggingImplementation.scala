package com.outr.robobrowser.logging

import com.outr.robobrowser.RoboBrowser

trait LoggingImplementation {
  protected def browser: RoboBrowser

  def apply(): List[LogEntry]

  def clear(): Unit

  def debug(message: String, args: AnyRef*): Unit =
    browser.execute("console.debug(arguments[0]);", message :: args.toList*)
  def error(message: String, args: AnyRef*): Unit =
    browser.execute("console.error(arguments[0]);", message :: args.toList*)
  def info(message: String, args: AnyRef*): Unit =
    browser.execute("console.info(arguments[0]);", message :: args.toList*)
  def trace(message: String, args: AnyRef*): Unit =
    browser.execute("console.trace(arguments[0]);", message :: args.toList*)
  def warn(message: String, args: AnyRef*): Unit =
    browser.execute("console.warn(arguments[0]);", message :: args.toList*)
}