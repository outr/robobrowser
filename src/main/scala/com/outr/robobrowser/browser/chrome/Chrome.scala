package com.outr.robobrowser.browser.chrome

import com.outr.robobrowser.RoboBrowser
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions => SeleniumChromeOptions}
import org.openqa.selenium.devtools.v120.network.Network
import org.openqa.selenium.devtools.v120.network.model.Headers

import java.io.{File, FileNotFoundException}
import java.util
import java.util.{Base64, Optional}

class Chrome(options: SeleniumChromeOptions) extends RoboBrowser(options) {
  override type Driver = ChromeDriver

  override def sessionId: String = "Chrome"

  def setBasicAuth(username: String, password: String): Unit = withDriver { driver =>
    val devTools = driver.getDevTools
    devTools.createSession()
    val ov = Optional.of[Integer](100000)
    devTools.send(Network.enable(ov, ov, ov))
    val auth = s"$username:$password"
    val encoded = Base64.getEncoder.encodeToString(auth.getBytes("UTF-8"))
    val headers = new util.HashMap[String, AnyRef]
    headers.put("Authorization", s"Basic $encoded")
    devTools.send(Network.setExtraHTTPHeaders(new Headers(headers)))
    ()
  }

  override protected def createDriver(): ChromeDriver = new ChromeDriver(options)
}

object Chrome extends ChromeOptions(new SeleniumChromeOptions, None, new util.HashMap[String, Any], Nil) {
  // TODO: Support auto-download of chromedriver from https://chromedriver.storage.googleapis.com/index.html
  // TODO: Detect installed version of Chrome using google-chrome --version
  private val searchPaths = List("/usr/bin/chromedriver", "/opt/homebrew/bin/chromedriver")
  private lazy val resolvedDriver: Option[File] = searchPaths
    .map(path => new File(path))
    .find(_.isFile)

  def findChromeDriver(): String = resolvedDriver
    .map(_.getCanonicalPath)
    .getOrElse(throw new FileNotFoundException(s"Unable to find ChromeDriver in ${searchPaths.mkString(", ")}"))
}