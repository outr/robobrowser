package com.outr.robobrowser

import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.LocalFileDetector
import spice.net.URL

import java.util

abstract class Capabilities(val map: Map[String, Any], val prefs: util.HashMap[String, Any]) {
  type C <: Capabilities

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
    prefs.put("profile", profile)
    withCapabilities("prefs" -> ExperimentalOption(prefs))
  }

  def disablePasswordManager: C = {
    prefs.put("credentials_enable_service", false)
    prefs.put("profile.password_manager_enabled", false)
    withCapabilities("prefs" -> ExperimentalOption(prefs))
  }

  def app(url: URL): C = withArguments("app" -> s"--app=$url")

  def kiosk: C = withArguments("kios" -> "--kiosk")

  def disableControlledBar: C =
    withCapabilities("excludeSwitches" -> ExperimentalOption(Array("enable-automation")))

  def mobileEmulation(deviceName: String,
                      width: Int = -1,
                      height: Int = -1,
                      pixelRatio: Double = -1.0,
                      touch: Boolean = true): C = {
    val mobileEmulation = new util.HashMap[String, AnyRef]
    mobileEmulation.put("deviceName", deviceName)
    if (width != -1) mobileEmulation.put("width", Integer.valueOf(width))
    if (height != -1) mobileEmulation.put("height", Integer.valueOf(height))
    if (pixelRatio != -1.0) mobileEmulation.put("pixelRatio", java.lang.Double.valueOf(pixelRatio))
    if (!touch) mobileEmulation.put("touch", java.lang.Boolean.valueOf(touch))
    withCapabilities("mobileEmulation" -> ExperimentalOption(mobileEmulation))
  }

  def disableJavaScript: C = withCapabilities(
    "javascript.enabled" -> false,
    "chrome.switches" -> util.Arrays.asList("--disable-javascript")
  ).asInstanceOf[C].withArguments("disable-javascript" -> "--disable-javascript").asInstanceOf[C]

  def headless: C = withCapabilities("headless" -> Argument("--headless"))

  def disableGPU: C = withCapabilities("disable-gpu" -> Argument("--disable-gpu"))

  def browser(browser: Browser): C = withCapabilities(
    "browser" -> browser.value,
    "browserName" -> browser.value
  )

  def windowSize(width: Int, height: Int): C = withArguments("window-size" -> s"--window-size=$width,$height")

  def enableDRM: C = withArguments("media.eme.enabled" -> "true", "media.gmp-manager.updateEnabled" -> "true")

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

  def apply(map: Map[String, Any]): Capabilities = new Capabilities(map, new util.HashMap[String, Any]) {
    override type C = Capabilities

    override def ++(that: Capabilities): C = Capabilities(this.map ++ that.map)
  }
}