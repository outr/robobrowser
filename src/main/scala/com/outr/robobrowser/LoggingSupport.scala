package com.outr.robobrowser

trait LoggingSupport {
  def apply(): List[LogEntry]

  def clear(): Unit
}