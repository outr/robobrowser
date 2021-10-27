package com.outr.robobrowser.integration

case class EqualityComparison[T](expected: T) extends Comparison[T] {
  override def compareWith(value: T): Unit = if (value == expected) {
    ()
  } else {
    throw AssertionFailed(s"'$value' was not equal to '$expected''")
  }

  override def compareNot(value: T): Unit = if (value == expected) {
    throw AssertionFailed(s"'$value' was equal to '$expected''")
  } else {
    ()
  }
}