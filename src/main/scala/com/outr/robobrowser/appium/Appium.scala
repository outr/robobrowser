package com.outr.robobrowser.appium

import com.outr.robobrowser.RoboBrowser

trait Appium extends RoboBrowser {
  def iOSVersion: Option[Int] = this match {
    case ios: RoboIOS => Some(ios.version)
    case _ => None
  }

  def nativeAllow(reject: Boolean = false): Boolean
  def home(): Unit
}