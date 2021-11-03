package com.outr.robobrowser

import java.io.{File, FileWriter, PrintWriter}
import java.util.Date
import io.youi.http.cookie.ResponseCookie
import io.youi.net.URL
import org.openqa.selenium.{By, Cookie, JavascriptExecutor, Keys, OutputType, TakesScreenshot, WebDriver, WindowType}
import io.youi.stream._
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.interactions.Actions

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import perfolation._
import reactify.Trigger

import scala.annotation.tailrec
import scala.util.Try

trait RoboBrowser extends AbstractElement {
  val pageChanged: Trigger = Trigger()

  protected def logCapabilities: Boolean = false

  private var _disposed: Boolean = false
  private var lastVerifiedWindow: Long = 0L

  override protected def instance: RoboBrowser = this

  def options: BrowserOptions

  protected var capabilities: ChromeOptions = _

  private val _initialized = new AtomicBoolean(false)

  private lazy val _driver: WebDriver = {
    val options = this.options.toCapabilities
    configureOptions(options)
    capabilities = options
    createWebDriver(options)
  }

  protected def driver: WebDriver = {
    val initted = init()
    try {
      verifyWindowInitialized()
      _driver
    } finally {
      if (initted) postInit()
    }
  }

  protected def configureOptions(options: ChromeOptions): Unit = {}

  protected def createWebDriver(options: ChromeOptions): WebDriver

  final def initialized: Boolean = _initialized.get()

  protected def initialize(): Unit = {
    verifyWindowInitialized()
  }

  private val verifying = new AtomicBoolean(false)

  protected def verifyWindowInitialized(): Unit = if (verifying.compareAndSet(false, true)) {
    try {
      val now = System.currentTimeMillis()
      val elapsed = now - lastVerifiedWindow
      if (elapsed > 1000L) {
        Try {
          lastVerifiedWindow = now
          val loaded = execute("return typeof window.roboBrowserInitialized !== 'undefined';").asInstanceOf[Boolean]
          if (!loaded) {
            val input = getClass.getClassLoader.getResourceAsStream("extras.js")
            val script = IO.stream(input, new StringBuilder).toString
            execute(script)
            pageChanged.trigger()     // Notify that the page has changed
          }
        }.failed.foreach { t =>
          scribe.debug(s"Error while attempting to initialize window: ${t.getMessage}")
        }
      }
    } finally {
      verifying.set(false)
    }
  }

  final def init(): Boolean = if (_initialized.compareAndSet(false, true)) {
    initialize()
    true
  } else {
    false
  }

  final def postInit(): Unit = {
    if (logCapabilities) {
      val caps = capabilities.getCapabilityNames.asScala.toList.map { key =>
        val value = capabilities.getCapability(key)
        s"  $key = $value (${value.getClass.getName})"
      }.mkString("\n")
      scribe.info(s"Creating RoboBrowser with the following capabilities:\n$caps")
    }
  }

  def load(url: URL): Unit = driver.get(url.toString())
  def url: URL = URL(driver.getCurrentUrl)
  def content: String = driver.getPageSource
  def save(file: File): Unit = IO.stream(content, file)
  def screenshot(file: File): Unit = {
    val bytes = driver.asInstanceOf[TakesScreenshot].getScreenshotAs(OutputType.BYTES)
    IO.stream(bytes, file)
  }

  def sessionId: String

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

  def readyState: ReadyState = Try(execute("return document.readyState;").toString match {
    case "loading" => ReadyState.Loading
    case "interactive" => ReadyState.Interactive
    case "complete" => ReadyState.Complete
  }).getOrElse(ReadyState.Loading)

  def sleep(duration: FiniteDuration): Unit = Thread.sleep(duration.toMillis)

  def title: String = driver.getTitle

  def execute(script: String, args: AnyRef*): AnyRef = driver.asInstanceOf[JavascriptExecutor].executeScript(script, args: _*)

  def debug(message: String, args: AnyRef*): Unit = execute("console.debug(arguments[0]);", message :: args.toList: _*)
  def error(message: String, args: AnyRef*): Unit = execute("console.error(arguments[0]);", message :: args.toList: _*)
  def info(message: String, args: AnyRef*): Unit = execute("console.info(arguments[0]);", message :: args.toList: _*)
  def trace(message: String, args: AnyRef*): Unit = execute("console.trace(arguments[0]);", message :: args.toList: _*)
  def warn(message: String, args: AnyRef*): Unit = execute("console.warn(arguments[0]);", message :: args.toList: _*)

  def action: Actions = new Actions(driver)

  object keyboard {
    object send {
      def apply(charSequence: CharSequence*): Unit = action.sendKeys(charSequence: _*).perform()
      def up(): Unit = apply(Keys.ARROW_UP)
      def down(): Unit = apply(Keys.ARROW_DOWN)
      def left(): Unit = apply(Keys.ARROW_LEFT)
      def right(): Unit = apply(Keys.ARROW_RIGHT)
    }
    object press {
      def apply(charSequence: CharSequence): Unit = action.keyDown(charSequence).perform()
    }
  }

  object window {
    private def w: WebDriver.Window = driver.manage().window()

    def maximize(): Unit = w.maximize()
    def handle: WindowHandle = WindowHandle(driver.getWindowHandle)
    def handles: Set[WindowHandle] = driver.getWindowHandles.asScala.toSet.map(WindowHandle.apply)
    def switchTo(handle: WindowHandle): Unit = driver.switchTo().window(handle.handle)
    def newTab(): WindowHandle = {
      driver.switchTo().newWindow(WindowType.TAB)
      handle
    }
    def close(): Unit = driver.close()
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
    def apply(): List[LogEntry] = execute("return window.logs;")
      .asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
      .asScala
      .toList
      .map { map =>
        val level = map.get("level") match {
          case "trace" => LogLevel.Trace
          case "debug" => LogLevel.Debug
          case "info" => LogLevel.Info
          case "warn" => LogLevel.Warning
          case "error" => LogLevel.Error
          case _ => LogLevel.Info
        }
        val timestamp = map.get("timestamp").asInstanceOf[Long]
        val message = map.get("message").toString
        LogEntry(level, timestamp, message)
      }

    def clear(): Unit = execute("console.clear();")
  }

  def saveLogs(file: File): Unit = {
    val w = new PrintWriter(new FileWriter(file))
    try {
      logs().foreach { e =>
        val l = e.timestamp
        val d = s"${l.t.Y}.${l.t.m}.${l.t.d} ${l.t.T}:${l.t.L}"
        w.println(s"$d - ${e.level} - ${e.message}")
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