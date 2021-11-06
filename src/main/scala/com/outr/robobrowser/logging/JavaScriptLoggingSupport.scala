package com.outr.robobrowser.logging

import com.outr.robobrowser.{RoboBrowser, logging}
import io.youi.stream.IO

import scala.jdk.CollectionConverters._

trait JavaScriptLoggingSupport extends LoggingSupport { b =>
  override protected def initWindow(): Unit = {
    super.initWindow()

    val input = getClass.getClassLoader.getResourceAsStream("js-logging.js")
    val script = IO.stream(input, new StringBuilder).toString
    execute(script)
  }

  override object logs extends LoggingImplementation {
    override protected def browser: RoboBrowser = b

    override def apply(): List[LogEntry] = execute("return window.logs;")
      .asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
      .asScala
      .toList
      .map { map =>
        val level = map.get("level") match {
          case "trace" => LogLevel.Trace
          case "debug" => LogLevel.Debug
          case "info" => LogLevel.Info
          case "warn" => LogLevel.Warning
          case "error" => LogLevel.Error
          case _ => LogLevel.Info
        }
        val timestamp = map.get("timestamp").asInstanceOf[Long]
        val message = map.get("message").toString
        logging.LogEntry(level, timestamp, message)
      }

    override def clear(): Unit = execute("console.clear();")
  }
}