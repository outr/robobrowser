package com.outr.robobrowser

object Context {
  lazy val Native: Context = Context("NATIVE_APP")
  lazy val Current: Context = Context("Current")
}

case class Context(value: String) {
  override def toString: String = value
}