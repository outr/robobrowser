package com.outr.robobrowser.chrome

import com.outr.robobrowser.{BrowserOptions, Device}

case class ChromeOptions(headless: Boolean = true,
                         device: Device = Device.Chrome,
                         fakeMedia: Boolean = true,
                         driverPath: String = "/usr/bin/chromedriver") extends BrowserOptions