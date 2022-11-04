package com.outr.robobrowser.appium

import com.outr.robobrowser.RoboBrowser
import com.outr.robobrowser.browser.android.Android
import com.outr.robobrowser.browser.ios.IOS

trait Appium extends RoboBrowser {
  lazy val iOSVersion: Option[Int] = if (isIOS) {
    Some(version.toInt)
  } else {
    None
  }

  lazy val androidVersion: Option[Double] = if (isAndroid) {
    Some(version)
  } else {
    None
  }

  def version: Double

  lazy val isAndroid: Boolean = this.isInstanceOf[Android]
  lazy val isIOS: Boolean = this.isInstanceOf[IOS]

  def nativeAllow(reject: Boolean = false): Boolean
  def home(): Unit
}