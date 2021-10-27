package com.outr.robobrowser.integration

trait Comparison[T] {
  def compareWith(value: T): Unit
}