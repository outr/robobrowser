package com.outr.robobrowser.appium

import com.outr.robobrowser.{RoboBrowser, RoboBrowserBuilder}

class IOSCapabilities[T <: RoboBrowser](builder: RoboBrowserBuilder[T]) {
  private def caps(capabilities: (String, Any)*): RoboBrowserBuilder[RoboIOS] = builder
    .withCapabilities(
      "os" -> "ios",
      "browser" -> "iphone",
      "real_mobile" -> true
    )
    .withCapabilities(capabilities: _*)
    .withCreator(RoboIOS.create)

  object `iPhone 8 Plus` {
    def `v12`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "12",
      "device" -> "iPhone 8 Plus"
    )
    def `v11`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "11",
      "device" -> "iPhone 8 Plus"
    )
  }
  object `iPad Pro 12.9 2017` {
    def `v11`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "11",
      "device" -> "iPad Pro 12.9 2017"
    )
  }
  object `iPhone XS Max` {
    def `v12`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "12",
      "device" -> "iPhone XS Max"
    )
  }
  object `iPhone 7` {
    def `v12`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "12",
      "device" -> "iPhone 7"
    )
    def `v10`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "10",
      "device" -> "iPhone 7"
    )
  }
  object `iPad 5th` {
    def `v11`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "11",
      "device" -> "iPad 5th"
    )
  }
  object `iPhone X` {
    def `v11`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "11",
      "device" -> "iPhone X"
    )
  }
  object `iPhone 6S Plus` {
    def `v11`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "11",
      "device" -> "iPhone 6S Plus"
    )
  }
  object `iPad Mini 4` {
    def `v11`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "11",
      "device" -> "iPad Mini 4"
    )
  }
  object `iPad 7th` {
    def `v13`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "13",
      "device" -> "iPad 7th"
    )
  }
  object `iPhone 11` {
    def `v14`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "14",
      "device" -> "iPhone 11"
    )
    def `v13`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "13",
      "device" -> "iPhone 11"
    )
  }
  object `iPhone 11 Pro Max` {
    def `v14`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "14",
      "device" -> "iPhone 11 Pro Max"
    )
    def `v13`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "13",
      "device" -> "iPhone 11 Pro Max"
    )
  }
  object `iPad Pro 12.9 2021` {
    def `v14`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "14",
      "device" -> "iPad Pro 12.9 2021"
    )
  }
  object `iPad Pro 12.9 2020` {
    def `v14`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "14",
      "device" -> "iPad Pro 12.9 2020"
    )
    def `v13`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "13",
      "device" -> "iPad Pro 12.9 2020"
    )
  }
  object `iPad Air 4` {
    def `v14`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "14",
      "device" -> "iPad Air 4"
    )
  }
  object `iPad 6th` {
    def `v11`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "11",
      "device" -> "iPad 6th"
    )
  }
  object `iPad Air 2019` {
    def `v13`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "13",
      "device" -> "iPad Air 2019"
    )
    def `v12`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "12",
      "device" -> "iPad Air 2019"
    )
  }
  object `iPad Pro 11 2018` {
    def `v12`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "12",
      "device" -> "iPad Pro 11 2018"
    )
  }
  object `iPhone XR` {
    def `v12`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "12",
      "device" -> "iPhone XR"
    )
  }
  object `iPhone 11 Pro` {
    def `v15`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "15",
      "device" -> "iPhone 11 Pro"
    )
    def `v13`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "13",
      "device" -> "iPhone 11 Pro"
    )
  }
  object `iPhone 12 Pro Max` {
    def `v14`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "14",
      "device" -> "iPhone 12 Pro Max"
    )
  }
  object `iPad 8th` {
    def `v14`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "14",
      "device" -> "iPad 8th"
    )
  }
  object `iPad Pro 12.9 2018` {
    def `v15`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "15",
      "device" -> "iPad Pro 12.9 2018"
    )
    def `v13`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "13",
      "device" -> "iPad Pro 12.9 2018"
    )
    def `v12`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "12",
      "device" -> "iPad Pro 12.9 2018"
    )
  }
  object `iPhone 6S` {
    def `v12`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "12",
      "device" -> "iPhone 6S"
    )
    def `v11`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "11",
      "device" -> "iPhone 6S"
    )
  }
  object `iPad Mini 2019` {
    def `v13`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "13",
      "device" -> "iPad Mini 2019"
    )
    def `v12`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "12",
      "device" -> "iPad Mini 2019"
    )
  }
  object `iPhone SE` {
    def `v11`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "11",
      "device" -> "iPhone SE"
    )
  }
  object `iPhone 6` {
    def `v11`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "11",
      "device" -> "iPhone 6"
    )
  }
  object `iPad Pro 11 2020` {
    def `v13`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "13",
      "device" -> "iPad Pro 11 2020"
    )
  }
  object `iPhone 7 Plus` {
    def `v10`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "10",
      "device" -> "iPhone 7 Plus"
    )
  }
  object `iPhone 12 Mini` {
    def `v14`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "14",
      "device" -> "iPhone 12 Mini"
    )
  }
  object `iPad Pro 9.7 2016` {
    def `v11`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "11",
      "device" -> "iPad Pro 9.7 2016"
    )
  }
  object `iPhone 12 Pro` {
    def `v14`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "14",
      "device" -> "iPhone 12 Pro"
    )
  }
  object `iPhone 12` {
    def `v14`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "14",
      "device" -> "iPhone 12"
    )
  }
  object `iPhone SE 2020` {
    def `v13`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "13",
      "device" -> "iPhone SE 2020"
    )
  }
  object `iPhone 8` {
    def `v15`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "15",
      "device" -> "iPhone 8"
    )
    def `v13`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "13",
      "device" -> "iPhone 8"
    )
    def `v12`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "12",
      "device" -> "iPhone 8"
    )
    def `v11`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "11",
      "device" -> "iPhone 8"
    )
  }
  object `iPhone XS` {
    def `v15`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "15",
      "device" -> "iPhone XS"
    )
    def `v14`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "14",
      "device" -> "iPhone XS"
    )
    def `v13`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "13",
      "device" -> "iPhone XS"
    )
    def `v12`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "12",
      "device" -> "iPhone XS"
    )
  }
  object `iPad Pro 11 2021` {
    def `v14`: RoboBrowserBuilder[RoboIOS] = caps(
      "os_version" -> "14",
      "device" -> "iPad Pro 11 2021"
    )
  }
}