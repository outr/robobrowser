package com.outr.robobrowser

case class Rect(x: Int, y: Int, width: Int, height: Int) {
  lazy val x2: Int = x + width
  lazy val y2: Int = y + height
}