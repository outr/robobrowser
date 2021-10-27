package com.outr.robobrowser.appium

trait Appium {
  def inNativeContext[Return](f: => Return): Return
  def nativeAllow(reject: Boolean = false): Unit
}