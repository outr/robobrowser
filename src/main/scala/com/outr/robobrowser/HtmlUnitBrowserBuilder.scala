package com.outr.robobrowser
import com.gargoylesoftware.htmlunit.BrowserVersion
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.htmlunit.HtmlUnitDriver
import scribe.{Level, Logger}

object HtmlUnitBrowserBuilder {
  // Disable extraneous logging from htmlunit
  Logger("com.gargoylesoftware.htmlunit").withMinimumLevel(Level.Error).replace()

  def create(capabilities: Capabilities): RoboBrowser = {
    new RoboBrowser(capabilities) {
      override type Driver = HtmlUnitDriver

      override def sessionId: String = "HtmlUnit"

      override protected def createWebDriver(options: ChromeOptions): Driver = {
        val enableJS = capabilities.typed[Boolean]("javascript.enabled", true)
        val driver = new HtmlUnitDriver(BrowserVersion.CHROME, enableJS)
        driver
      }
    }
  }
}