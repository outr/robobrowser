package com.outr.robobrowser

import com.outr.robobrowser.logging.{LogEntry, LogLevel, LoggingImplementation}

import java.io.{File, FileWriter, PrintWriter}
import java.util.Date
import io.youi.http.cookie.ResponseCookie
import io.youi.net.URL
import org.openqa.selenium.{By, Cookie, JavascriptExecutor, Keys, OutputType, TakesScreenshot, WebDriver, WindowType}
import io.youi.stream._
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.remote.RemoteWebDriver
import perfolation._

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import reactify.{Channel, Trigger, Var}

import scala.annotation.tailrec
import scala.util.Try

abstract class RoboBrowser(val capabilities: Capabilities) extends AbstractElement { rb =>
  type Driver <: WebDriver

  val paused: Var[Boolean] = Var(false)

  private val _ignorePause = new ThreadLocal[Boolean] {
    override def initialValue(): Boolean = false
  }

  /**
   * If true, checks to make sure the window is initialized before each instruction is invoked, but no faster than every
   * one second (defaults to true)
   */
  var verifyWindowInitializationCheck: Boolean = true

  val pageChanged: Trigger = Trigger()
  val configuringOptions: Channel[ChromeOptions] = Channel[ChromeOptions]
  val initializing: Channel[Driver] = Channel[Driver]
  val loading: Channel[URL] = Channel[URL]
  val loaded: Channel[URL] = Channel[URL]
  val disposing: Trigger = Trigger()

  /**
   * If set to true, will log the Selenium capabilities after init. Defaults to false.
   */
  protected def logCapabilities: Boolean = false

  /**
   * Injects an arbitrary delay between each action invoked on the browser. Defaults to 0.
   */
  protected def delay: FiniteDuration = 0.seconds

  private var _disposed: Boolean = false
  private var lastVerifiedWindow: Long = 0L

  override protected def instance: RoboBrowser = this

  protected var chromeOptions: ChromeOptions = _

  private val _initialized = new AtomicBoolean(false)

  private lazy val _driver: Driver = {
    val options = new ChromeOptions
    capabilities(options)
    configureOptions(options)
    chromeOptions = options
    createWebDriver(options)
  }

  def sessionId: String

  protected def createWebDriver(options: ChromeOptions): Driver

  protected def withDriver[Return](f: Driver => Return): Return = {
    val initted = init()
    try {
      verifyWindowInitialized()
      if (delay > 0.seconds) {
        sleep(delay)              // Arbitrary sleep between each call
      }
      while (paused() && !_ignorePause.get()) {
        sleep(250.millis)
      }
      synchronized {
        f(_driver)
      }
    } finally {
      if (initted) postInit()
    }
  }

  def ignoringPause[Return](f: => Return): Return = {
    _ignorePause.set(true)
    try {
      f
    } finally {
      _ignorePause.remove()
    }
  }

  protected def configureOptions(options: ChromeOptions): Unit = {
    configuringOptions @= options
  }

  final def initialized: Boolean = _initialized.get()

  protected def initialize(): Unit = {
    verifyWindowInitialized()
    withDriver { driver =>
      initializing @= driver
    }
  }

  private val verifying = new AtomicBoolean(false)

  protected def verifyWindowInitialized(): Unit = if (verifyWindowInitializationCheck && verifying.compareAndSet(false, true)) {
    try {
      val now = System.currentTimeMillis()
      val elapsed = now - lastVerifiedWindow
      if (elapsed > 1000L) {
        Try {
          lastVerifiedWindow = now
          val loaded = execute("return typeof window.roboBrowserInitialized !== 'undefined';").asInstanceOf[Boolean]
          if (!loaded) {
            initWindow()
            execute("window.roboBrowserInitialized = true;")
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

  protected def initWindow(): Unit = {
    val input = getClass.getClassLoader.getResourceAsStream("js-logging.js")
    val script = IO.stream(input, new StringBuilder).toString
    execute(script)
  }

  final def init(): Boolean = if (_initialized.compareAndSet(false, true)) {
    initialize()
    true
  } else {
    false
  }

  final def postInit(): Unit = {
    if (logCapabilities) {
      val caps = chromeOptions.getCapabilityNames.asScala.toList.map { key =>
        val value = chromeOptions.getCapability(key)
        s"  $key = $value (${value.getClass.getName})"
      }.mkString("\n")
      scribe.info(s"Creating RoboBrowser with the following capabilities:\n$caps")
    }
  }

  def load(url: URL): Unit = {
    loading @= url
    withDriver { driver =>
      driver.get(url.toString())
    }
    loaded @= url
  }
  def url: URL = URL(withDriver(_.getCurrentUrl))
  def content: String = withDriver(_.getPageSource)
  def save(file: File): Unit = IO.stream(content, file)
  def screenshot(file: File): Unit = {
    val bytes = capture()
    IO.stream(bytes, file)
  }

  override def capture(): Array[Byte] = withDriver(_.asInstanceOf[TakesScreenshot].getScreenshotAs(OutputType.BYTES))

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

  def title: String = withDriver(_.getTitle)

  def execute(script: String, args: AnyRef*): AnyRef = withDriver(_.asInstanceOf[JavascriptExecutor].executeScript(script, args: _*))

  def action: Actions = withDriver(driver => new Actions(driver))

  object keyboard {
    object send {
      def apply(charSequence: CharSequence*): Unit = action.sendKeys(charSequence: _*).perform()
      def up(): Unit = apply(Keys.ARROW_UP)
      def down(): Unit = apply(Keys.ARROW_DOWN)
      def left(): Unit = apply(Keys.ARROW_LEFT)
      def right(): Unit = apply(Keys.ARROW_RIGHT)
      def home(): Unit = apply(Keys.HOME)
    }
    object press {
      def apply(charSequence: CharSequence): Unit = action.keyDown(charSequence).perform()
    }
  }

  object window {
    private def w: WebDriver.Window = withDriver(_.manage().window())

    def maximize(): Unit = w.maximize()
    def handle: WindowHandle = withDriver(driver => WindowHandle(driver.getWindowHandle))
    def handles: Set[WindowHandle] = withDriver(_.getWindowHandles.asScala.toSet.map(WindowHandle.apply))
    def switchTo(handle: WindowHandle): Unit = withDriver(_.switchTo().window(handle.handle))
    def newTab(): WindowHandle = {
      withDriver(_.switchTo().newWindow(WindowType.TAB))
      handle
    }
    def close(): Unit = withDriver(_.close())
  }

  override def by(by: By): List[WebElement] = withDriver(_.findElements(by).asScala.toList.map(new SeleniumWebElement(_, this)))

  def cookies: List[ResponseCookie] = withDriver(_.manage().getCookies.asScala.toList.map { cookie =>
    ResponseCookie(
      name = cookie.getName,
      value = cookie.getValue,
      expires = Option(cookie.getExpiry).map(_.getTime),
      domain = Option(cookie.getDomain),
      path = Option(cookie.getPath),
      secure = cookie.isSecure,
      httpOnly = cookie.isHttpOnly
    )
  })

  def cookies_=(cookies: List[ResponseCookie]): Unit = {
    val options = withDriver(_.manage())
    options.deleteAllCookies()
    cookies.foreach { c =>
      options.addCookie(new Cookie(c.name, c.value, c.domain.orNull, c.path.orNull, c.expires.map(new Date(_)).orNull, c.secure, c.httpOnly))
    }
  }

  lazy val logs: LoggingImplementation = new LoggingImplementation {
    override protected def browser: RoboBrowser = rb

    override def apply(): List[LogEntry] = execute("return window.logs;")
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
        logging.LogEntry(level, timestamp, message)
      }

    override def clear(): Unit = execute("console.clear();")
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
      disposing.trigger()
      _disposed = true
      withDriver(_.quit())
    }
  }
}

object RoboBrowser extends RoboBrowserBuilder[RoboBrowser](creator = _ => throw new NotImplementedError("You must define an implementation")) {
  private def createChrome(capabilities: Capabilities): RoboBrowser = {
    new RoboBrowser(capabilities) {
      override type Driver = ChromeDriver

      override def sessionId: String = "Chrome"

      override protected def createWebDriver(options: ChromeOptions): Driver = {
        System.setProperty("webdriver.chrome.driver", capabilities.typed[String]("driverPath", "/usr/bin/chromedriver"))
        new ChromeDriver(options)
      }
    }
  }

  private def createRemote(capabilities: Capabilities): RoboBrowser = {
    new RoboBrowser(capabilities) {
      override type Driver = RemoteWebDriver

      override def sessionId: String = withDriver(_.getSessionId.toString)

      override protected def createWebDriver(options: ChromeOptions): Driver = {
        val url = new java.net.URL(capabilities.typed[String]("url", "http://localhost:4444"))
        new RemoteWebDriver(url, options)
      }
    }
  }

  object Chrome extends RoboBrowserBuilder[RoboBrowser](createChrome)
  object Remote extends RoboBrowserBuilder[RoboBrowser](createRemote)
}