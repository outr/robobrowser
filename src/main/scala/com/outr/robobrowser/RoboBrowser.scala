package com.outr.robobrowser

import java.io.File
import java.util.{Date, TimeZone}

import com.machinepublishers.jbrowserdriver.{JBrowserDriver, Settings, Timezone}
import io.youi.http.cookie.ResponseCookie
import io.youi.net.URL
import org.openqa.selenium.{By, Cookie, Dimension, OutputType}
import io.youi.stream._

import scala.jdk.CollectionConverters._

class RoboBrowser(timeZone: TimeZone = TimeZone.getDefault,
                  device: Device = Device.Chrome) extends AbstractElement {
  private lazy val settings = Settings
    .builder()
    .userAgent(device.userAgent)
    .screen(new Dimension(device.width, device.height))
    .timezone(Timezone.byName(timeZone.getID))
    .build()
  private lazy val driver = new JBrowserDriver(settings)

  def load(url: URL): Unit = driver.get(url.toString())
  def status: Int = driver.getStatusCode
  def url: URL = URL(driver.getCurrentUrl)
  def pageWait(): Unit = driver.pageWait()
  def content: String = driver.getPageSource
  def save(file: File): Unit = IO.stream(content, file)
  def screenshot(file: File): Unit = {
    val bytes = driver.getScreenshotAs(OutputType.BYTES)
    IO.stream(bytes, file)
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

  def dispose(): Unit = driver.quit()
}