package com.outr.robobrowser.appium

import com.outr.robobrowser.RoboBrowser

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

  lazy val isAndroid: Boolean = this.isInstanceOf[RoboAndroid]
  lazy val isIOS: Boolean = this.isInstanceOf[RoboIOS]

  def nativeAllow(reject: Boolean = false): Boolean
  def home(): Unit
}