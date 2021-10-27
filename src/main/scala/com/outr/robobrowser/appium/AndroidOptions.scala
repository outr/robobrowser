package com.outr.robobrowser.appium

import com.outr.robobrowser.{Android, BrowserOptions, Device}
import io.youi.net._

case class AndroidOptions(headless: Boolean = false,
                          device: Device = Android.Samsung.S21.Ultra(),
                          fakeMedia: Boolean = true,
                          url: URL = url"http://localhost:4444") extends BrowserOptions