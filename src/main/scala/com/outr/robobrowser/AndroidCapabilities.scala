package com.outr.robobrowser

class AndroidCapabilities(capabilities: Capabilities) {
  object `Samsung Galaxy S21 Ultra` {
    def `v11.0`: Capabilities = capabilities.withCapabilities(
      "os" -> "android",
      "os_version" -> "11.0",
      "browser" -> "android",
      "device" -> "Samsung Galaxy S21 Ultra",
      "real_mobile" -> true
    )
  }
}