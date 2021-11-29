package com.outr.robobrowser

import com.outr.robobrowser.integration.AssertionFailed
import com.outr.robobrowser.logging.{LogEntry, LogLevel, LoggingImplementation}
import io.appium.java_client.PushesFiles
import io.appium.java_client.remote.SupportsContextSwitching

import java.io.{File, FileWriter, PrintWriter}
import java.util.Date
import io.youi.http.cookie.ResponseCookie
import io.youi.net.URL
import org.openqa.selenium.{By, Cookie, JavascriptExecutor, Keys, OutputType, TakesScreenshot, WebDriver, WindowType}
import io.youi.stream._
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.interactions.Actions
import perfolation._

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import reactify.{Channel, Trigger, Val, Var}

import scala.annotation.tailrec
import scala.concurrent.TimeoutException
import scala.util.Try

abstract class RoboBrowser(val capabilities: Capabilities) extends AbstractElement { rb =>
  type Driver <: WebDriver

  private lazy val mainContext: Context = scs(scs => {
    scs
      .getContextHandles
      .asScala
      .toList
      .map(Context.apply)
      .filterNot(_ == Context.Native) match {
        case context :: Nil => context
        case Nil => throw new RuntimeException("No non-native context found")
        case list => throw new RuntimeException(s"Multiple non-native contexts found: ${list.mkString(", ")}")
      }
  }).getOrElse(Context("Browser"))

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

  private lazy val _context: Var[Context] = Var(mainContext)
  lazy val context: Val[Context] = _context

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

  override protected def browser: RoboBrowser = this

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

  protected def withDriver[Return](f: Driver => Return): Return = withDriverAndContext(Context.Browser)(f)

  private def scs[Return](f: SupportsContextSwitching => Return): Option[Return] = _driver match {
    case scs: SupportsContextSwitching => Some(f(scs))
    case _ => None
  }

  def withDriverAndContext[Return](context: Context)(f: Driver => Return): Return = {
    val initted = init()
    try {
      verifyWindowInitialized()
      if (delay > 0.seconds) {
        sleep(delay)              // Arbitrary sleep between each call
      }
      while (paused() && !_ignorePause.get()) {
        sleep(250.millis)
      }
      rb.synchronized {
        val realContext = context match {
          case Context.Browser => mainContext
          case _ => context
        }
        if (_context() != realContext && realContext != Context.Current) {
          scs(_.context(realContext.value))
          _context @= realContext
        }
        f(_driver)
      }
    } finally {
      if (initted) postInit()
    }
  }

  def ignoringPause[Return](f: => Return): Return = {
    val previous = _ignorePause.get()
    _ignorePause.set(true)
    try {
      f
    } finally {
      _ignorePause.set(previous)
    }
  }

  protected def configureOptions(options: ChromeOptions): Unit = {
    configuringOptions @= options
  }

  final def initialized: Boolean = _initialized.get()

  protected def initialize(): Unit = {
    scs(_.context(mainContext.value))    // Make sure the initial context is not native
    verifyWindowInitialized()
    withDriver { driver =>
      initializing @= driver
    }
  }

  private val verifying = new AtomicBoolean(false)

  protected def verifyWindowInitialized(): Unit = if (verifyWindowInitializationCheck && context() != Context.Native && verifying.compareAndSet(false, true)) {
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
  def content(context: Context = Context.Browser): String = withDriverAndContext(context)(_.getPageSource)
  def save(file: File, context: Context = Context.Browser): Unit = IO.stream(content(context), file)
  def screenshot(file: File): Unit = {
    val bytes = capture()
    IO.stream(bytes, file)
  }

  def atPoint(x: Int, y: Int, context: Context = Context.Browser): Option[WebElement] = {
    val result = execute("return document.elementFromPoint(arguments[0], arguments[1]);", Integer.valueOf(x), Integer.valueOf(y))
    val e = Option(result.asInstanceOf[org.openqa.selenium.WebElement])
    e.map { element =>
      new SeleniumWebElement(element, context, this)
    }
  }

  def size: (Int, Int) = {
    val width = execute("return window.innerWidth;").asInstanceOf[Long]
    val height = execute("return window.innerHeight;").asInstanceOf[Long]
    (width.toInt, height.toInt)
  }

  override def capture(): Array[Byte] = withDriverAndContext(Context.Current)(_.asInstanceOf[TakesScreenshot].getScreenshotAs(OutputType.BYTES))

  def waitFor(timeout: FiniteDuration, sleep: FiniteDuration = 500.millis)(condition: => Boolean): Boolean =
    waitForResult[Boolean](timeout, sleep, timeoutResult = false) {
      if (condition) {
        Some(true)
      } else {
        None
      }
    }

  def waitForResult[Return](timeout: FiniteDuration,
                            sleep: FiniteDuration = 500.millis,
                            timeoutResult: => Return = throw new TimeoutException("Condition timed out"))
                           (condition: => Option[Return]): Return = withDriver { _ =>
    val end = System.currentTimeMillis() + timeout.toMillis

    @tailrec
    def recurse(): Return = condition match {
      case Some(r) => r
      case None if System.currentTimeMillis() >= end => timeoutResult
      case None =>
        this.sleep(sleep)
        recurse()
    }

    recurse()
  }

  def pushFile(remotePath: String, file: File): Boolean = withDriver {
    case driver: PushesFiles =>
      driver.pushFile(remotePath, file)
      true
    case _ => false
  }

  def waitForLoaded(timeout: FiniteDuration = 15.seconds): Unit = if (!waitFor(timeout) {
    readyState == ReadyState.Complete
  }) {
    throw AssertionFailed(s"Browser's readyState was not complete after $timeout: $readyState")
  }

  def readyState: ReadyState = Try(execute("return document.readyState;").toString match {
    case "loading" => ReadyState.Loading
    case "interactive" => ReadyState.Interactive
    case "complete" => ReadyState.Complete
  }).getOrElse(ReadyState.Loading)

  def sleep(duration: FiniteDuration): Unit = Thread.sleep(duration.toMillis)

  def title: String = withDriver(_.getTitle)

  def execute(script: String, args: AnyRef*): AnyRef = withDriverAndContext(Context.Browser) { driver =>
    val fixed = args.map {
      case arg: SeleniumWebElement => SeleniumWebElement.underlying(arg)    // Extract the WebElement
      case arg => arg
    }
    driver.asInstanceOf[JavascriptExecutor].executeScript(script, fixed: _*)
  }

  def executeTyped[T](script: String, args: AnyRef*): T = execute(script, args: _*).asInstanceOf[T]

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

  override def by(by: By): List[WebElement] = this.by(by, Context.Browser)

  def by(by: By, context: Context): List[WebElement] = withDriverAndContext(context) { driver =>
    driver.findElements(by).asScala.toList.map(new SeleniumWebElement(_, context, this))
  }

  override def children: List[WebElement] = Nil

  final def oneBy(by: By, context: Context): WebElement = this.by(by, context) match {
    case element :: Nil => element
    case Nil => throw new RuntimeException(s"Nothing found by selector: ${by.toString}")
    case list => throw new RuntimeException(s"More than one found by selector: ${by.toString} ($list)")
  }
  def firstBy(by: By, context: Context): Option[WebElement] = this.by(by, context).headOption

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

    override def apply(): List[LogEntry] = Option(execute("return window.logs;")) match {
      case None =>
        scribe.warn(s"Logs returned null")
        Nil
      case Some(list) if list.isInstanceOf[java.util.List[_]] => list
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
      case result =>
        scribe.warn(s"Error while retrieving logs! Got: $result instead of list")
        Nil
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
  def debug(directory: File,
            name: String,
            saveHTML: Boolean = true,
            saveNative: Boolean = true,
            saveScreenshot: Boolean = true,
            saveLogs: Boolean = true): Unit = {
    directory.mkdirs()
    if (saveNative) Try(save(new File(directory, s"$name-native.xml"), Context.Native)).failed.foreach { t =>
      scribe.warn(s"Error while attempting to save native: ${t.getMessage}")
    }
    if (saveHTML) Try(save(new File(directory, s"$name.html"), Context.Browser)).failed.foreach { t =>
      scribe.warn(s"Error while attempting to save HTML: ${t.getMessage}")
    }
    if (saveScreenshot) Try(screenshot(new File(directory, s"$name.png"))).failed.foreach { t =>
      scribe.warn(s"Error while attempting to save screenshot: ${t.getMessage}")
    }
    if (saveLogs) Try(this.saveLogs(new File(directory, s"$name.log"))).failed.foreach { t =>
      scribe.warn(s"Error while attempting to save logs: ${t.getMessage}")
    }
  }

  override def outerHTML: String = content()

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
  object Chrome extends RoboBrowserBuilder[RoboBrowser](ChromeBrowserBuilder.create)
  object Remote extends RoboBrowserBuilder[RoboBrowser](RemoteBrowserBuilder.create)
}