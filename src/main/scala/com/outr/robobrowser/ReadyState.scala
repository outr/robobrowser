package com.outr.robobrowser

sealed trait ReadyState

object ReadyState {
  case object Loading extends ReadyState
  case object Interactive extends ReadyState
  case object Complete extends ReadyState
}