package com.outr.robobrowser

import io.youi.net.URL
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.LocalFileDetector

import java.util

trait Capabilities {
  type C <: Capabilities

  def map: Map[String, Any]

  def apply(options: ChromeOptions): Unit = map.foreach {
    case (key, value) => value match {
      case _: Transient => scribe.debug(s"Excluding $key")
      case Argument(arg) => options.addArguments(arg)
      case ExperimentalOption(arg) => options.setExperimentalOption(key, arg)
      case _ => options.setCapability(key, value)
    }
  }

  def ++(that: Capabilities): C

  def withCapabilities(pairs: (String, Any)*): C = ++(Capabilities(pairs.toMap))

  def withArguments(pairs: (String, String)*): C = ++(Capabilities(pairs.map {
    case (key, value) => key -> Argument(value)
  }.toMap))

  def get(key: String): Option[Any] = map.get(key)

  def apply(key: String): Any = map.getOrElse(key, s"Unable to find $key capability: ${map.keys.mkString(", ")}")

  def typed[T](key: String): T = apply(key).asInstanceOf[T]

  def typed[T](key: String, default: => T): T = getTyped[T](key).getOrElse(default)

  def getTyped[T](key: String): Option[T] = get(key).asInstanceOf[Option[T]]

  def contains(key: String): Boolean = map.contains(key)

  def url(url: URL): C = withCapabilities("url" -> url.toString())

  def userDataDir(path: String): C = withArguments("user-data-dir" -> s"--user-data-dir=$path", "profile-directory" -> "--profile-directory=Default")

  def notifications(notifications: Notifications): C = {
    val n = notifications match {
      case Notifications.Default => 0
      case Notifications.Allow => 1
      case Notifications.Block => 2
    }
    val contentSettings = new util.HashMap[String, AnyRef]
    contentSettings.put("notifications", Integer.valueOf(n))
    val profile = new util.HashMap[String, AnyRef]
    profile.put("managed_default_content_settings", contentSettings)
    val prefs = new util.HashMap[String, AnyRef]
    prefs.put("profile", profile)
    withCapabilities("prefs" -> ExperimentalOption(prefs))
  }

  def headless: C = withCapabilities("headless" -> Argument("--headless"))

  def disableGPU: C = withCapabilities("disable-gpu" -> Argument("--disable-gpu"))

  def browser(browser: Browser): C = withCapabilities(
    "browser" -> browser.value,
    "browserName" -> browser.value
  )

  def windowSize(width: Int, height: Int): C = withArguments("window-size" -> s"--window-size=$width,$height")

  def maximized: C = withArguments("start-maximized" -> "--start-maximized")

  def device(id: String): C = withCapabilities("device" -> id)

  def osVersion(v: String): C = withCapabilities("os_version" -> v)

  def realMobile(b: Boolean = true): C = withCapabilities("realMobile" -> b)

  def ignoreCertificateErrors: C = withArguments("ignore-certificate-errors" -> "--ignore-certificate-errors")

  def noSandbox: C = withArguments("no-sandbox" -> "--no-sandbox")

  def disableDevSHMUsage: C = withArguments("disable-dev-shm-usage" -> "--disable-dev-shm-usage")

  def userAgent(ua: String): C = withArguments("user-agent" -> s"user-agent=$ua")

  def fakeMedia: C = withArguments(
    "use-fake-device-for-media-stream" -> "use-fake-device-for-media-stream",
    "use-fake-ui-for-media-stream" -> "use-fake-ui-for-media-stream"
  )

  def chromeDriver(path: String): C = withCapabilities("driverPath" -> path)

  def localFileDetector: C = withCapabilities("fileDetector" -> new LocalFileDetector() with Transient)
}

object Capabilities {
  def apply(pairs: (String, Any)*): Capabilities = apply(pairs.toMap)

  def apply(capabilities: Map[String, Any]): Capabilities = new Capabilities {
    override type C = Capabilities

    override def ++(that: Capabilities): C = Capabilities(this.map ++ that.map)

    override def map: Map[String, Any] = capabilities
  }
}