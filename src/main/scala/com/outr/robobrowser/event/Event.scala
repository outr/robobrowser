package com.outr.robobrowser.event

import com.outr.robobrowser.WebElement

case class Event[T](key: String, value: T, element: Option[WebElement])