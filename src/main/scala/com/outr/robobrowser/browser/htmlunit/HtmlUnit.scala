package com.outr.robobrowser.browser.htmlunit

import com.gargoylesoftware.htmlunit.BrowserVersion
import com.outr.robobrowser.RoboBrowser
import org.openqa.selenium.ImmutableCapabilities
import org.openqa.selenium.htmlunit.HtmlUnitDriver

class HtmlUnit(javaScript: Boolean) extends RoboBrowser(new ImmutableCapabilities) {
  override type Driver = HtmlUnitDriver

  override def sessionId: String = "HtmlUnit"

  override protected def createDriver(): Driver = {
    new HtmlUnitDriver(BrowserVersion.CHROME, javaScript)
  }
}

object HtmlUnit extends HtmlUnitOptions(true)