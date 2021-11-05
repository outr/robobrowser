package com.outr.robobrowser.integration

case class Comparison[T](f: T => Boolean,
                         failMessage: T => String,
                         failNotMessage: T => String,
                         failMessageOverride: Option[() => String] = None) {
  def compareWith(value: T): Unit = f(value) match {
    case true => ()
    case false => throw AssertionFailed(failMessageOverride.map(_()).getOrElse(failMessage(value)))
  }

  def compareNot(value: T): Unit = f(value) match {
    case true => throw AssertionFailed(failMessageOverride.map(_()).getOrElse(failNotMessage(value)))
    case false => ()
  }
}