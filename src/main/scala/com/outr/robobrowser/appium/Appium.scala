package com.outr.robobrowser.appium

import com.outr.robobrowser.RoboBrowser

trait Appium extends RoboBrowser {
  def inNativeContext[Return](f: => Return): Return
  def nativeAllow(reject: Boolean = false): Unit
  def home(): Unit
}