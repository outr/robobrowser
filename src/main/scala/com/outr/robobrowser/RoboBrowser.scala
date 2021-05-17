package com.outr.robobrowser

import java.io.{File, FileWriter, PrintWriter}
import java.util.Date
import io.youi.http.cookie.ResponseCookie
import io.youi.net.URL
import org.openqa.selenium.{By, Cookie, JavascriptExecutor, OutputType, TakesScreenshot, WebDriver}
import io.youi.stream._
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.interactions.HasInputDevices
import org.openqa.selenium.logging.{LogEntry, LogType}
import org.openqa.selenium.support.ui.WebDriverWait

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

import perfolation._

class RoboBrowser(device: Device = Device.Chrome,
                  loader: DriverLoader = DriverLoader.Chrome()) extends AbstractElement {
  private lazy val options = {
    val o = new ChromeOptions
    configureOptions(o)
    o
  }
  private lazy val _driver: WebDriver = loader(options)

  protected final def driver: WebDriver = {
    init()
    _driver
  }

  protected def configureOptions(options: ChromeOptions): Unit = {
    if (loader.headless) {
      options.addArguments(
        "--headless",
        "--disable-gpu"
      )
    }
    options.addArguments(
      s"--window-size=${device.width},${device.height}",
      "--ignore-certificate-errors",
      "--no-sandbox",
      "--disable-dev-shm-usage"
    )
    device.userAgent.foreach { ua =>
      options.addArguments(s"user-agent=$ua")
    }
    if (device.emulateMobile) {
      val deviceMetrics = new java.util.HashMap[String, Any]
      deviceMetrics.put("width", device.width)
      deviceMetrics.put("height", device.height)
      val mobileEmulation = new java.util.HashMap[String, Any]
      mobileEmulation.put("deviceMetrics", deviceMetrics)
      mobileEmulation.put("userAgent", device.userAgent)
      options.setExperimentalOption("mobileEmulation", mobileEmulation)
    }
  }

  private val _initialized = new AtomicBoolean(false)
  final def initialized: Boolean = _initialized.get()

  protected def initialize(): Unit = {}

  final def init(): Unit = if (_initialized.compareAndSet(false, true)) {
    initialize()
  }

  def load(url: URL): Unit = driver.get(url.toString())
  def url: URL = URL(driver.getCurrentUrl)
  def content: String = driver.getPageSource
  def save(file: File): Unit = IO.stream(content, file)
  def screenshot(file: File): Unit = {
    val bytes = driver.asInstanceOf[TakesScreenshot].getScreenshotAs(OutputType.BYTES)
    IO.stream(bytes, file)
  }

  def waitFor(timeout: FiniteDuration, condition: => Boolean): Unit = {
    val wait = new WebDriverWait(driver, timeout.toSeconds)
    wait.until((_: WebDriver) => condition)
  }

  def title: String = driver.getTitle

  def execute(script: String, args: AnyRef*): AnyRef = driver.asInstanceOf[JavascriptExecutor].executeScript(script, args: _*)

  object keyboard {
    object arrow {
      def down(): Unit = driver.asInstanceOf[HasInputDevices].getKeyboard.sendKeys("""\xEE\x80\x95""")
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

  def logs(`type`: String = LogType.BROWSER): List[LogEntry] = driver.manage().logs().get(`type`).asScala.toList.sortBy(_.getTimestamp)

  def saveLogs(file: File, `type`: String = LogType.BROWSER): Unit = {
    val w = new PrintWriter(new FileWriter(file))
    try {
      logs(`type`).foreach { e =>
        val l = e.getTimestamp
        val d = s"${l.t.Y}.${l.t.m}.${l.t.d} ${l.t.T}:${l.t.L}"
        w.println(s"$d - ${e.getLevel.getName} - ${e.getMessage}")
      }
    } finally {
      w.flush()
      w.close()
    }
  }

  /**
   * Saves HTML, Screenshot, and Browser logs for current page
   *
   * @param directory the directory to write the data for
   * @param name prefix name for each file created
   */
  def debug(directory: File, name: String): Unit = {
    save(new File(directory, s"$name.html"))
    screenshot(new File(directory, s"$name.png"))
    saveLogs(new File(directory, s"$name.log"))
  }

  override def outerHTML: String = content

  override def innerHTML: String = {
    val head = by("head").headOption.map(_.outerHTML).getOrElse("")
    val body = by("body").headOption.map(_.outerHTML).getOrElse("")
    s"$head$body"
  }

  def dispose(): Unit = driver.quit()
}