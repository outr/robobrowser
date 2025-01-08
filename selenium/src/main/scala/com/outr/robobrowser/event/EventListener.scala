package com.outr.robobrowser.event

trait EventListener[T] {
  def apply(event: Event[T]): Unit
}