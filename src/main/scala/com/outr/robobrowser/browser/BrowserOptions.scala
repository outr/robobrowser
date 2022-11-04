package com.outr.robobrowser.browser

import org.openqa.selenium.{Capabilities, ImmutableCapabilities}
import spice.net.URL

import scala.jdk.CollectionConverters._

trait BrowserOptions[O] {
  def capabilities: Capabilities

  def withCapabilities(caps: (String, Any)*): O = {
    val map = caps.toMap.asJava
    merge(new ImmutableCapabilities(map))
  }

  def get(key: String): Option[Any] = Option(capabilities.getCapability(key))

  def apply(key: String): Any = get(key).getOrElse(key, s"Unable to find $key capability: ${capabilities.asMap().asScala.keys.mkString(", ")}")

  def typed[T](key: String): T = apply(key).asInstanceOf[T]

  def typed[T](key: String, default: => T): T = getTyped[T](key).getOrElse(default)

  def getTyped[T](key: String): Option[T] = get(key).asInstanceOf[Option[T]]

  def contains(key: String): Boolean = capabilities.is(key)

  def merge(capabilities: Capabilities): O

  def url(url: URL): O = withCapabilities("url" -> url.toString())
}
