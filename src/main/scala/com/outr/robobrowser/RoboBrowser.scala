package com.outr.robobrowser

import java.io.File
import java.util.Date

import io.youi.http.cookie.ResponseCookie
import io.youi.net.URL
import org.openqa.selenium.{By, Cookie, OutputType, WebDriver}
import io.youi.stream._
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}

import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

class RoboBrowser(headless: Boolean = true, device: Device = Device.Chrome) extends AbstractElement {
  private lazy val options = {
    System.setProperty("webdriver.chrome.driver", "/usr/bin/chromedriver")
    val o = new ChromeOptions
    if (headless) {
      o.addArguments(
        "--headless",
        "--disable-gpu"
      )
    }
    o.addArguments(
      s"--window-size=${device.width},${device.height}",
      "--ignore-certificate-errors",
      "--no-sandbox",
      "--disable-dev-shm-usage"
    )
    device.userAgent.foreach { ua =>
      o.addArguments(s"user-agent=$ua")
    }
    if (device.emulateMobile) {
      val deviceMetrics = new java.util.HashMap[String, Any]
      deviceMetrics.put("width", device.width)
      deviceMetrics.put("height", device.height)
      val mobileEmulation = new java.util.HashMap[String, Any]
      mobileEmulation.put("deviceMetrics", deviceMetrics)
      mobileEmulation.put("userAgent", device.userAgent)
      o.setExperimentalOption("mobileEmulation", mobileEmulation)
    }
    o
  }
  private lazy val driver = new ChromeDriver(options)

  def load(url: URL): Unit = driver.get(url.toString())
  def url: URL = URL(driver.getCurrentUrl)
  def content: String = driver.getPageSource
  def save(file: File): Unit = IO.stream(content, file)
  def screenshot(file: File): Unit = {
    val bytes = driver.getScreenshotAs(OutputType.BYTES)
    IO.stream(bytes, file)
  }

  def waitFor(timeout: FiniteDuration, condition: => Boolean): Unit = {
    val wait = new WebDriverWait(driver, timeout.toSeconds)
    wait.until((_: WebDriver) => condition)
  }

  def title: String = driver.getTitle

  def execute(script: String, args: AnyRef*): AnyRef = driver.executeScript(script, args: _*)

  object keyboard {
    object arrow {
      def down(): Unit = driver.getKeyboard.sendKeys("""\xEE\x80\x95""")
    }
  }

  override def by(by: By): List[WebElement] = driver.findElements(by).asScala.toList.map(new WebElement(_))

  def cookies: List[ResponseCookie] = driver.manage().getCookies.asScala.toList.map { cookie =>
    ResponseCookie(
      name = cookie.getName,
      value = cookie.getValue,
      expires = Option(cookie.getExpiry).map(_.getTime),
      domain = Option(cookie.getDomain),
      path = Option(cookie.getPath),
      secure = cookie.isSecure,
      httpOnly = cookie.isHttpOnly
    )
  }

  def cookies_=(cookies: List[ResponseCookie]): Unit = {
    val options = driver.manage()
    options.deleteAllCookies()
    cookies.foreach { c =>
      options.addCookie(new Cookie(c.name, c.value, c.domain.orNull, c.path.orNull, c.expires.map(new Date(_)).orNull, c.secure, c.httpOnly))
    }
  }

  override def outerHTML: String = content

  override def innerHTML: String = {
    val head = by("head").headOption.map(_.outerHTML).getOrElse("")
    val body = by("body").headOption.map(_.outerHTML).getOrElse("")
    s"$head$body"
  }

  def dispose(): Unit = driver.quit()
}