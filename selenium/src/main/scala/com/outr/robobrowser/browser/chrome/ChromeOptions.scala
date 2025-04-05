package com.outr.robobrowser.browser.chrome

import com.outr.robobrowser.{BrowserType, Notifications, RoboBrowser}
import com.outr.robobrowser.browser.BrowserOptions
import org.openqa.selenium.Capabilities
import org.openqa.selenium.chrome.{ChromeOptions => SeleniumChromeOptions}
import org.openqa.selenium.remote.LocalFileDetector
import spice.net.URL

import java.io.File
import java.util

case class ChromeOptions(options: SeleniumChromeOptions,
                         driverPath: Option[String] = None,
                         prefs: util.HashMap[String, Any],
                         postInit: List[RoboBrowser => Unit]) extends BrowserOptions[ChromeOptions] {
  override def withPostInit(f: RoboBrowser => Unit): ChromeOptions = copy(postInit = postInit ::: List(f))

  override def capabilities: Capabilities = options
  override def merge(capabilities: Capabilities): ChromeOptions = copy(options.merge(capabilities))

  def driverPath(path: String): ChromeOptions = copy(driverPath = Some(path))

  protected def add(f: SeleniumChromeOptions => SeleniumChromeOptions): ChromeOptions = {
    val o = new SeleniumChromeOptions
    copy(options = options.merge(f(o)))
  }

  protected def withArguments(arguments: String*): ChromeOptions = add(_.addArguments(arguments*))

  def headless: ChromeOptions = withArguments("--headless")

  def remoteAllowOrigins(value: String = "*"): ChromeOptions = withArguments(s"--remote-allow-origins=*")

  def windowSize(width: Int, height: Int): ChromeOptions = withArguments(s"--window-size=$width,$height")

  def userDataDir(path: String): ChromeOptions = withArguments(
    s"--user-data-dir=$path",
    "--profile-directory=Default"
  )

  def notifications(notifications: Notifications): ChromeOptions = {
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
    add(_.setExperimentalOption("prefs", prefs))
  }

  def disablePasswordManager: ChromeOptions = {
    prefs.put("credentials_enable_service", false)
    prefs.put("profile.password_manager_enabled", false)
    add(_.setExperimentalOption("prefs", prefs))
  }

  def app(url: URL): ChromeOptions = withArguments(s"--app=$url")

  def kiosk: ChromeOptions = withArguments("--kiosk")

  def disableControlBar: ChromeOptions = add(_.setExperimentalOption("excludeSwitches", Array("enable-automation")))

  def mobileEmulation(deviceName: String,
                      width: Int = -1,
                      height: Int = -1,
                      pixelRatio: Double = -1.0,
                      touch: Boolean = true): ChromeOptions = {
    val mobileEmulation = new util.HashMap[String, AnyRef]
    mobileEmulation.put("deviceName", deviceName)
    if (width != -1) mobileEmulation.put("width", Integer.valueOf(width))
    if (height != -1) mobileEmulation.put("height", Integer.valueOf(height))
    if (pixelRatio != -1.0) mobileEmulation.put("pixelRatio", java.lang.Double.valueOf(pixelRatio))
    if (!touch) mobileEmulation.put("touch", java.lang.Boolean.valueOf(touch))
    add(_.setExperimentalOption("mobileEmulation", mobileEmulation))
  }

  def disableJavaScript: ChromeOptions = withCapabilities(
    "javascript.enabled" -> false,
    "chrome.switches" -> util.Arrays.asList("--disable-javascript")
  ).withArguments("--disable-javascript")

  def disableGPU: ChromeOptions = withArguments("--disable-gpu")

  def browser(browser: BrowserType): ChromeOptions = withCapabilities(
    "browser" -> browser.value,
    "browserName" -> browser.value
  )

  def maximized: ChromeOptions = withArguments("--start-maximized")

  def device(id: String): ChromeOptions = withCapabilities("device" -> id)

  def osVersion(v: String): ChromeOptions = withCapabilities("os_version" -> v)

  def realMobile(b: Boolean = true): ChromeOptions = withCapabilities("realMobile" -> b)

  def ignoreCertificateErrors: ChromeOptions = withArguments("--ignore-certificate-errors")

  def ignoreSSLErrors: ChromeOptions = withArguments("--ignore-ssl-errors=yes")

  def noSandbox: ChromeOptions = withArguments("--no-sandbox")

  def disableDevSHMUsage: ChromeOptions = withArguments("--disable-dev-shm-usage")

  def userAgent(userAgent: String): ChromeOptions = withArguments(s"user-agent=$userAgent")

  def fakeMedia: ChromeOptions = withArguments(
    "use-fake-device-for-media-stream",
    "use-fake-ui-for-media-stream"
  )

  def chromeDriver(path: String): ChromeOptions = withCapabilities("driverPath" -> path)

  def localFileDetector: ChromeOptions = withCapabilities("fileDetector" -> new LocalFileDetector())

  /**
   * Use a proxy server.
   *
   * @param proxy proxy server to use (ex. socks5://localhost:1234)
   */
  def proxyServer(proxy: String): ChromeOptions = withArguments(s"--proxy-server=$proxy")

  def addExtensions(paths: File*): ChromeOptions = add { options =>
    options.addExtensions(paths*)
  }

  def create(): Chrome = {
    System.setProperty("webdriver.chrome.driver", driverPath.getOrElse(Chrome.findChromeDriver()))
    val b = new Chrome(options)
    postInit.foreach(f => f(b))
    b
  }
}
