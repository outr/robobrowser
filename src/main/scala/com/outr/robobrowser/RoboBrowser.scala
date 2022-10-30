package com.outr.robobrowser

import com.outr.robobrowser.integration.AssertionFailed
import com.outr.robobrowser.logging.{LogEntry, LogLevel, LoggingImplementation}
import io.appium.java_client.PushesFiles
import io.appium.java_client.remote.SupportsContextSwitching

import java.io.{File, FileWriter, PrintWriter}
import java.util.Date
import org.openqa.selenium.{Cookie, JavascriptExecutor, Keys, OutputType, TakesScreenshot, WebDriver, WindowType}
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.html5.{LocalStorage, SessionStorage, WebStorage}
import org.openqa.selenium.interactions.Actions
import perfolation._

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import reactify.{Channel, Trigger, Val, Var}

import scala.annotation.tailrec
import scala.concurrent.TimeoutException
import scala.util.Try
import fabric._
import fabric.io.{JsonFormatter, JsonParser}
import fabric.rw.{Asable, Convertible, RW}
import spice.http.cookie.SameSite
import spice.http.cookie.{Cookie => SpiceCookie}
import spice.net.URL
import spice.streamer._

import scala.collection.mutable

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
    val id: String = sessionId
    scribe.info(s"Initialized session id: $id")
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
            scribe.info("Initializing window...")
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
    val script = Streamer(input, new mutable.StringBuilder).toString
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
  def save(file: File, context: Context = Context.Browser): Unit = Streamer(content(context), file)
  def screenshot(file: File): Unit = {
    val bytes = capture()
    Streamer(bytes, file)
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

  def fullScreen(): Unit = withDriver(_.manage().window().fullscreen())

  def readyState: ReadyState = Try(execute("return document.readyState;").toString match {
    case "loading" => ReadyState.Loading
    case "interactive" => ReadyState.Interactive
    case "complete" => ReadyState.Complete
  }).getOrElse(ReadyState.Loading)

  def sleep(duration: FiniteDuration): Unit = Thread.sleep(duration.toMillis)

  def title: String = withDriver(_.getTitle)

  def supportsJavaScript: Boolean = withDriver(_.isInstanceOf[JavascriptExecutor])

  def execute(script: String, args: AnyRef*): AnyRef = withDriverAndContext(Context.Browser) { driver =>
    val fixed = args.map {
      case arg: SeleniumWebElement => SeleniumWebElement.underlying(arg)    // Extract the WebElement
      case arg => arg
    }
    driver match {
      case e: JavascriptExecutor => e.executeScript(script, fixed: _*)
      case _ => null    // Ignore not supported
    }
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

  object navigate {
    def refresh(): Unit = withDriver(_.navigate().refresh())
    def back(): Unit = withDriver(_.navigate().back())
    def forward(): Unit = withDriver(_.navigate().forward())
  }

  override def by(by: By): List[WebElement] = withDriverAndContext(by.context) { driver =>
    val sby = by.`type`.create(by.value)
    driver
      .findElements(sby)
      .asScala
      .toList
      .map(new SeleniumWebElement(_, context, this))
  }

  override def children: List[WebElement] = Nil

  object storage {
    object cookies {
      private implicit val ssRW: RW[SameSite] = RW.from(
        r = (ss: SameSite) => ss match {
          case SameSite.Normal => "normal"
          case SameSite.Lax => "lax"
          case SameSite.Strict => "strict"
        },
        w = _.asString match {
          case "normal" => SameSite.Normal
          case "lax" => SameSite.Lax
          case "strict" => SameSite.Strict
          case s => throw new RuntimeException(s"Unsupported value for SameSite: $s")
        }
      )
      private implicit val rw: RW[SpiceCookie.Response] = RW.gen

      private def c2rc(cookie: Cookie): SpiceCookie.Response = SpiceCookie.Response(
        name = cookie.getName,
        value = cookie.getValue,
        expires = Option(cookie.getExpiry).map(_.getTime),
        domain = Option(cookie.getDomain),
        path = Option(cookie.getPath),
        secure = cookie.isSecure,
        httpOnly = cookie.isHttpOnly
      )

      private def rc2c(cookie: SpiceCookie.Response): Cookie = new Cookie(
        cookie.name,
        cookie.value,
        cookie.domain.orNull,
        cookie.path.orNull,
        cookie.expires.map(new Date(_)).orNull,
        cookie.secure,
        cookie.httpOnly
      )

      def all: List[SpiceCookie.Response] = withDriver(_.manage().getCookies.asScala.toList.map(c2rc))

      def set(cookies: List[SpiceCookie.Response]): Unit = withDriver { driver =>
        val options = driver.manage()
        options.deleteAllCookies()
        cookies.foreach { c =>
          options.addCookie(rc2c(c))
        }
      }

      def add(cookies: List[SpiceCookie.Response]): Unit = withDriver { driver =>
        val options = driver.manage()
        cookies.foreach { c =>
          options.addCookie(rc2c(c))
        }
      }

      def get(name: String): Option[SpiceCookie.Response] = Option(withDriver(_.manage().getCookieNamed(name))).map(c2rc)

      def clear(): Unit = withDriver(_.manage().deleteAllCookies())

      def toJson: Json = all.json
      def fromJson(value: Json): List[SpiceCookie.Response] = value.as[List[SpiceCookie.Response]]

      def save(file: File): Unit = {
        val jsonString = JsonFormatter.Default(toJson)
        Streamer(jsonString, file)
      }

      def load(file: File): Boolean = if (file.isFile) {
        val jsonString = Streamer(file, new mutable.StringBuilder).toString
        val json = JsonParser(jsonString)
        val cookies = fromJson(json)
        add(cookies)
        true
      } else {
        false
      }
    }

    object localStorage {
      private def ls[Return](f: LocalStorage => Return): Return = withDriver { driver =>
        f(driver.asInstanceOf[WebStorage].getLocalStorage)
      }

      def keys: Set[String] = ls(_.keySet().asScala.toSet)

      def map: Map[String, String] = {
        val set = keys
        ls { storage =>
          set.map(key => key -> storage.getItem(key)).toMap
        }
      }

      def set(map: Map[String, String]): Unit = ls { storage =>
        storage.clear()
        map.foreach {
          case (key, value) => storage.setItem(key, value)
        }
      }

      def add(map: Map[String, String]): Unit = ls { storage =>
        map.foreach {
          case (key, value) => storage.setItem(key, value)
        }
      }

      def get(key: String): Option[String] = ls(s => Option(s.getItem(key)))

      def remove(key: String): Option[String] = Option(ls(_.removeItem(key)))

      def size: Int = ls(_.size())

      def clear(): Unit = ls(_.clear())

      def toJson: Json = map.json
      def fromJson(value: Json): Map[String, String] = value.as[Map[String, String]]

      def save(file: File): Unit = {
        val jsonString = JsonFormatter.Default(toJson)
        Streamer(jsonString, file)
      }

      def load(file: File): Boolean = if (file.isFile) {
        val jsonString = Streamer(file, new mutable.StringBuilder).toString
        val json = JsonParser(jsonString)
        val cookies = fromJson(json)
        add(cookies)
        true
      } else {
        false
      }
    }

    object sessionStorage {
      private def ss[Return](f: SessionStorage => Return): Return = withDriver { driver =>
        f(driver.asInstanceOf[WebStorage].getSessionStorage)
      }

      def keys: Set[String] = ss(_.keySet().asScala.toSet)

      def map: Map[String, String] = {
        val set = keys
        ss { storage =>
          set.map(key => key -> storage.getItem(key)).toMap
        }
      }

      def set(map: Map[String, String]): Unit = ss { storage =>
        storage.clear()
        map.foreach {
          case (key, value) => storage.setItem(key, value)
        }
      }

      def add(map: Map[String, String]): Unit = ss { storage =>
        map.foreach {
          case (key, value) => storage.setItem(key, value)
        }
      }

      def get(key: String): Option[String] = ss(s => Option(s.getItem(key)))

      def remove(key: String): Option[String] = Option(ss(_.removeItem(key)))

      def size: Int = ss(_.size())

      def clear(): Unit = ss(_.clear())

      def toJson: Json = map.json
      def fromJson(value: Json): Map[String, String] = value.as[Map[String, String]]

      def save(file: File): Unit = {
        val jsonString = JsonFormatter.Default(toJson)
        Streamer(jsonString, file)
      }

      def load(file: File): Boolean = if (file.isFile) {
        val jsonString = Streamer(file, new mutable.StringBuilder).toString
        val json = JsonParser(jsonString)
        val cookies = fromJson(json)
        add(cookies)
        true
      } else {
        false
      }
    }

    def save(directory: File,
             includeCookies: Boolean = true,
             includeLocalStorage: Boolean = true,
             includeSessionStorage: Boolean = true): Unit = {
      directory.mkdirs()
      assert(directory.isDirectory)
      if (includeCookies) {
        val file = new File(directory, "cookies.json")
        cookies.save(file)
      }
      if (includeLocalStorage) {
        val file = new File(directory, "local_storage.json")
        localStorage.save(file)
      }
      if (includeSessionStorage) {
        val file = new File(directory, "session_storage.json")
        sessionStorage.save(file)
      }
    }

    def load(directory: File,
             includeCookies: Boolean = true,
             includeLocalStorage: Boolean = true,
             includeSessionStorage: Boolean = true): Unit = {
      if (directory.isDirectory) {
        if (includeCookies) {
          val file = new File(directory, "cookies.json")
          if (file.isFile) cookies.load(file)
        }
        if (includeLocalStorage) {
          val file = new File(directory, "local_storage.json")
          if (file.isFile) localStorage.load(file)
        }
        if (includeSessionStorage) {
          val file = new File(directory, "session_storage.json")
          if (file.isFile) sessionStorage.load(file)
        }
      }
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
    val head = by(By.tagName("head")).headOption.map(_.outerHTML).getOrElse("")
    val body = by(By.tagName("body")).headOption.map(_.outerHTML).getOrElse("")
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
  object Firefox extends RoboBrowserBuilder[RoboBrowser](FirefoxBrowserBuilder.create)
  object Remote extends RoboBrowserBuilder[RoboBrowser](RemoteBrowserBuilder.create)
  object Grid extends RoboBrowserBuilder[RoboBrowser](GridBrowserBuilder.create)
  object HtmlUnit extends RoboBrowserBuilder[RoboBrowser](HtmlUnitBrowserBuilder.create)
  object Jsoup extends RoboBrowserBuilder[RoboBrowser](JsoupWebDriver.create)
}