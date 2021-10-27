package com.outr.robobrowser.integration

case class EqualityComparison[T](expected: T) extends Comparison[T] {
  override def compareWith(value: T): Unit = if (value == expected) {
    ()
  } else {
    throw AssertionFailed(s"$value was not equal to $expected")
  }
}