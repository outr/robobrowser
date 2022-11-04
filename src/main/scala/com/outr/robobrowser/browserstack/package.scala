package com.outr.robobrowser

import com.outr.robobrowser.browser.BrowserOptions
import org.openqa.selenium.ImmutableCapabilities

import scala.jdk.CollectionConverters._

package object browserstack {
  implicit class BrowserStackBrowserOptions[Options <: BrowserOptions[Options]](options: Options) {
    def browserStack(o: BrowserStackOptions): Options = {
      def cap(name: String): Option[String] = Option(options.capabilities.getCapability(name)).map(_.toString)
      def t[V](key: String, value: Option[V]): Option[(String, Any)] = value.map(v => key -> v.toString)

      val bs = List(
        t("osVersion", cap("os_version")),
        t("deviceName", cap("device")),
        t("realMobile", cap("real_mobile")),
        t("projectName", Some(o.projectName)),
        t("buildName", Some(o.buildName)),
        t("sessionName", Some(o.sessionName.getOrElse(s"${cap("device").get} ${cap("browser").get}"))),
        t("local", Some(o.local)),
        t("networkLogs", Some(o.networkLogs)),
        t("idleTimeout", Some(o.idleTimeout)),
        t("appiumVersion", Some(o.appiumVersion)),
        t("consoleLogs", Some(o.consoleLogs))
      ).flatten.toMap.asJava
      options.withCapabilities(
        "bstack:options" -> bs,
        BrowserStack.keyName -> o
      ).url(o.url)
    }
  }
}