package com.outr.robobrowser

sealed trait LogLevel

object LogLevel {
  case object Error extends LogLevel
  case object Warning extends LogLevel
  case object Info extends LogLevel
  case object Debug extends LogLevel
  case object Trace extends LogLevel
}