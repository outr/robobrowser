package com.outr.robobrowser.integration

case class BooleanComparison[T](f: T => Boolean,
                                failMessage: T => String,
                                failNotMessage: T => String) extends Comparison[T] {
  override def compareWith(value: T): Unit = f(value) match {
    case true => ()
    case false => throw AssertionFailed(failMessage(value))
  }

  override def compareNot(value: T): Unit = f(value) match {
    case true => throw AssertionFailed(failNotMessage(value))
    case false => ()
  }
}