package com.outr.robobrowser

import scribe.output.LogOutput
import scribe.output.format.OutputFormat
import scribe.writer.Writer
import scribe.{Level, LogRecord}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Try

case class BrowserConsoleWriter(browserOption: () => Option[RoboBrowser]) extends Writer {
  val args: ListBuffer[String] = ListBuffer.empty

  override def write(record: LogRecord, output: LogOutput, outputFormat: OutputFormat): Unit =
    browserOption().foreach { browser =>
      val b = new mutable.StringBuilder
      args.clear()
      outputFormat.begin(b.append(_))
      outputFormat(output, b.append(_))
      outputFormat.end(b.append(_))

      Try(
        if (record.level >= Level.Error) {
          browser.logs.error(b.toString())
        } else if (record.level >= Level.Warn) {
          browser.logs.warn(b.toString())
        } else if (record.level >= Level.Info) {
          browser.logs.info(b.toString())
        } else if (record.level >= Level.Debug) {
          browser.logs.debug(b.toString())
        } else {
          browser.logs.trace(b.toString())
        }
      )
    }
}