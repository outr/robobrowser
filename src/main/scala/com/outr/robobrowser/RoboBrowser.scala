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

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import perfolation._

import scala.annotation.tailrec

trait RoboBrowser extends AbstractElement {
  private var _disposed: Boolean = false

  override protected def instance: RoboBrowser = this

  def options: BrowserOptions

  private val _initialized = new AtomicBoolean(false)

  private lazy val _driver: WebDriver = {
    val options = this.options.toCapabilities
    configureOptions(options)
    createWebDriver(options)
  }

  protected def driver: WebDriver = {
    init()
    _driver
  }

  protected def configureOptions(options: ChromeOptions): Unit = {}

  // TODO: Stop using ChromeOptions?
  protected def createWebDriver(options: ChromeOptions): WebDriver

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

  def waitFor(timeout: FiniteDuration, sleep: FiniteDuration = 500.millis)(condition: => Boolean): Boolean = {
    val end = System.currentTimeMillis() + timeout.toMillis

    @tailrec
    def recurse(): Boolean = {
      val result: Boolean = condition
      if (result) {
        true
      } else if (System.currentTimeMillis() >= end) {
        false
      } else {
        this.sleep(sleep)
        recurse()
      }
    }

    recurse()
  }

  def sleep(duration: FiniteDuration): Unit = Thread.sleep(duration.toMillis)

  def title: String = driver.getTitle

  def execute(script: String, args: AnyRef*): AnyRef = driver.asInstanceOf[JavascriptExecutor].executeScript(script, args: _*)

  def debug(message: String, args: AnyRef*): Unit = execute("console.debug(arguments[0])", message :: args.toList: _*)
  def error(message: String, args: AnyRef*): Unit = execute("console.error(arguments[0])", message :: args.toList: _*)
  def info(message: String, args: AnyRef*): Unit = execute("console.info(arguments[0])", message :: args.toList: _*)
  def trace(message: String, args: AnyRef*): Unit = execute("console.trace(arguments[0])", message :: args.toList: _*)
  def warn(message: String, args: AnyRef*): Unit = execute("console.warn(arguments[0])", message :: args.toList: _*)

  object keyboard {
    object arrow {
      def down(): Unit = driver.asInstanceOf[HasInputDevices].getKeyboard.sendKeys("""\xEE\x80\x95""")
    }
  }

  override def by(by: By): List[WebElement] = driver.findElements(by).asScala.toList.map(new SeleniumWebElement(_, this))

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

  object logs {
    private var cache: List[LogEntry] = Nil

    def apply(): List[LogEntry] = {
      val logs = driver.manage().logs().get(LogType.BROWSER).asScala.toList
      cache = (cache ::: logs).sortBy(_.getTimestamp)
      cache
    }

    def clear(): Unit = {
      cache = Nil
      driver.manage().logs()
    }
  }

  def saveLogs(file: File): Unit = {
    val w = new PrintWriter(new FileWriter(file))
    try {
      logs().foreach { e =>
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

  def isDisposed: Boolean = _disposed

  def dispose(): Unit = synchronized {
    if (!isDisposed) {
      _disposed = true
      driver.quit()
    }
  }
}