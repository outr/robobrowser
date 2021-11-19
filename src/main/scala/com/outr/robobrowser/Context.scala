package com.outr.robobrowser

object Context {
  lazy val Native: Context = Context("NATIVE_APP")
}

case class Context(value: String) {
  override def toString: String = value
}