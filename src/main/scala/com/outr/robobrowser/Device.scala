package com.outr.robobrowser

import io.youi.net.URL
import org.openqa.selenium.chrome.ChromeOptions

/*case class Device(list: List[Capability]) extends Capabilities {
  def toCapabilities: ChromeOptions = {
    val options = new ChromeOptions
    list.foreach { c =>
      options.setCapability(c.key, c.value)
    }
    options
  }

  def withCapabilities(capabilities: Capability*): Device = {
    val caps = capabilities.toList
    val keys = caps.map(_.key)
    copy(list.filterNot(c => keys.contains(c.key)) ::: caps)
  }
}

case class Browser(value: String) extends Capability {
  override def key: String = "browser"
}*/

trait Capabilities {
  protected def map: Map[String, Any]

  def apply(options: ChromeOptions): Unit = map.foreach {
    case (key, value) => value match {
      case Argument(arg) => options.addArguments(arg)
      case _ => options.setCapability(key, value)
    }
  }

  def ++(that: Capabilities): Capabilities = Capabilities(this.map ++ that.map)
  def withCapabilities(pairs: (String, Any)*): Capabilities = ++(Capabilities(pairs.toMap))

  def get(key: String): Option[Any] = map.get(key)
  def apply(key: String): Any = map.getOrElse(key, s"Unable to find $key capability: ${map.keys.mkString(", ")}")
  def typed[T](key: String): T = apply(key).asInstanceOf[T]

  def url(url: URL): Capabilities = withCapabilities("url" -> url.toString())
}

object Capabilities extends Capabilities {
  override protected def map: Map[String, Any] = Map.empty

  def apply(pairs: (String, Any)*): Capabilities = apply(pairs.toMap)

  def apply(capabilities: Map[String, Any]): Capabilities = new Capabilities {
    override protected def map: Map[String, Any] = capabilities
  }
}

case class Argument(value: String)