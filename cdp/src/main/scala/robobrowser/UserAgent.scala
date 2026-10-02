package robobrowser

import fabric._
import fabric.dsl._

import scala.sys.process._
import scala.util.Try

/** The identity a RoboBrowser presents to sites: always the ordinary desktop browser's, never headless Chrome's.
  *
  * Headless Chrome announces itself in two places: the User-Agent string ("HeadlessChrome/154.0.0.0") and the
  * client hints (`Sec-CH-UA` headers and `navigator.userAgentData`, whose brands name "HeadlessChrome"). Search
  * engines and bot walls block on either, so both are replaced: the string at launch with `--user-agent` (which also
  * covers popups and workers), and the string plus client hints on every tab with `Emulation.setUserAgentOverride`.
  */
object UserAgent {
  /** What the installed binary says it is, from `<binary> --version` ("Google Chrome 154.0.8037.92"). */
  case class Product(name: String, version: String) {
    lazy val major: Option[Int] = version.takeWhile(_ != '.').toIntOption

    /** The brand Chromium adds beside its own for this product, if any. */
    def brand: Option[String] = name match {
      case n if n.startsWith("Google Chrome") => Some("Google Chrome")
      case n if n.startsWith("Microsoft Edge") => Some("Microsoft Edge")
      case _ => None
    }

    /** Vivaldi and other derivatives number their own releases, so only these carry Chromium's major version. */
    def chromiumVersioned: Boolean = name.startsWith("Google Chrome") || name.startsWith("Chromium") ||
      name.startsWith("Microsoft Edge")
  }

  private val ProductPattern = """^(.*?)\s+(\d+(?:\.\d+)+)""".r.unanchored

  def product(browser: Browser): Option[Product] = browser.existingPaths.headOption.flatMap { path =>
    Try(Seq(path.getCanonicalPath, "--version").!!.trim).toOption.flatMap {
      case ProductPattern(name, version) => Some(Product(name.trim, version))
      case _ => None
    }
  }

  /** The platform token real desktop Chrome sends; Chrome reduces it to a fixed value per platform. */
  def platformToken: String = os match {
    case "macOS" => "Macintosh; Intel Mac OS X 10_15_7"
    case "Windows" => "Windows NT 10.0; Win64; x64"
    case _ => "X11; Linux x86_64"
  }

  /** The reduced User-Agent desktop Chrome sends for a major version. */
  def ordinary(major: Int): String =
    s"Mozilla/5.0 ($platformToken) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"

  /** `userAgent` with any headless marker turned into the ordinary browser's. */
  def unheadless(userAgent: String): String = userAgent.replace("HeadlessChrome/", "Chrome/")

  private val ChromeMajor = """Chrome/(\d+)""".r.unanchored

  /** Client hints matching `userAgent`, as `Emulation.setUserAgentOverride` takes them: the brands real Chrome lists
    * (its GREASE brand, Chromium, and the product's own), and the platform the string names. */
  def metadata(userAgent: String, product: Option[Product]): Option[Json] = userAgent match {
    case ChromeMajor(major) =>
      val full = product.filter(p => p.chromiumVersioned && p.major.contains(major.toInt)).map(_.version)
        .getOrElse(s"$major.0.0.0")
      def brands(version: String) = arr(
        (List("Not)A;Brand" -> (if (version.contains('.')) "8.0.0.0" else "8"), "Chromium" -> version) :::
          product.flatMap(_.brand).map(_ -> version).toList).map { case (b, v) => obj("brand" -> b, "version" -> v) }*
      )
      Some(obj(
        "brands" -> brands(major),
        "fullVersionList" -> brands(full),
        "fullVersion" -> full,
        "platform" -> os,
        "platformVersion" -> platformVersion,
        "architecture" -> architecture,
        "model" -> "",
        "mobile" -> false,
        "bitness" -> "64",
        "wow64" -> false
      ))
    case _ => None
  }

  private def os: String = System.getProperty("os.name", "").toLowerCase match {
    case n if n.contains("mac") => "macOS"
    case n if n.contains("win") => "Windows"
    case _ => "Linux"
  }

  private def platformVersion: String = os match {
    case "Linux" => System.getProperty("os.version", "").takeWhile(c => c.isDigit || c == '.')
    case _ => System.getProperty("os.version", "")
  }

  private def architecture: String = System.getProperty("os.arch", "") match {
    case a if a.contains("aarch64") || a.contains("arm") => "arm"
    case _ => "x86"
  }
}
