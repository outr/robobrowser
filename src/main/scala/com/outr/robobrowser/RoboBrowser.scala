package com.outr.robobrowser

import java.io.File

import com.machinepublishers.jbrowserdriver.{JBrowserDriver, Settings, Timezone, UserAgent}
import io.youi.http.cookie.ResponseCookie
import io.youi.net.URL
import org.openqa.selenium.By
import org.powerscala.io._

import scala.collection.JavaConverters._

class RoboBrowser extends AbstractElement {
  private lazy val settings = Settings
    .builder()
    .userAgent(UserAgent.CHROME)
    .timezone(Timezone.AMERICA_CHICAGO)
    .build()
  private lazy val driver = new JBrowserDriver(settings)

  def load(url: URL): Unit = driver.get(url.toString())
  def status: Int = driver.getStatusCode
  def url: URL = URL(driver.getCurrentUrl)
  def pageWait(): Unit = driver.pageWait()
  def content: String = driver.getPageSource
  def save(file: File): Unit = IO.stream(content, file)

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

  def dispose(): Unit = driver.quit()
}
