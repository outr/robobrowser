package com.outr.robobrowser.appium

import com.outr.robobrowser.{BrowserOptions, Device, iOS}
import io.youi.net._

case class IOSOptions(headless: Boolean = false,
                      device: Device = iOS.iPhone12ProMax,
                      fakeMedia: Boolean = true,
                      url: URL = url"http://localhost:4444") extends BrowserOptions