package com.outr.robobrowser

sealed trait Notifications

object Notifications {
  case object Default extends Notifications
  case object Allow extends Notifications
  case object Block extends Notifications
}