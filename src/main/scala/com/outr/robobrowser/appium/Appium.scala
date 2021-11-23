package com.outr.robobrowser.appium

import com.outr.robobrowser.RoboBrowser

trait Appium extends RoboBrowser {
  def nativeAllow(reject: Boolean = false): Boolean
  def home(): Unit
}