package com.outr.robobrowser.browser.htmlunit

import com.gargoylesoftware.htmlunit.BrowserVersion
import com.outr.robobrowser.RoboBrowser
import org.openqa.selenium.htmlunit.HtmlUnitDriver

class HtmlUnit(javaScript: Boolean) extends RoboBrowser {
  override type Driver = HtmlUnitDriver

  override def sessionId: String = "HtmlUnit"

  override protected lazy val _driver: Driver = {
    new HtmlUnitDriver(BrowserVersion.CHROME, javaScript)
  }
}

object HtmlUnit extends HtmlUnitOptions(true)