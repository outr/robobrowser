package robobrowser

import fabric._
import fabric.dsl._
import fabric.io.{JsonFormatter, JsonParser}

import java.io.File
import java.nio.file.{Files, Path}
import scala.util.Try

case class BrowserConfig(userDataDir: File = BrowserConfig.resolveDataDir("Default"),
                         headless: Boolean = true,
                         useNewHeadlessMode: Boolean = true,
                         disableBackgrounding: Boolean = false,
                         disableGPU: Boolean = false,
                         disableDevSHMUsage: Boolean = false,
                         disableWebSecurity: Boolean = false,
                         disableBackgroundNetworking: Boolean = false,
                         disablePopupBlocking: Boolean = false,
                         disableSiteIsolationTrials: Boolean = false,
                         disableWebGL: Boolean = false,
                         disableInfobars: Boolean = false,
                         disableSync: Boolean = false,
                         disableSoftwareRasterizer: Boolean = false,
                         allowRunningInsecureContent: Boolean = false,
                         singleProcess: Boolean = false,
                         disableCache: Boolean = false,
                         enableAutomation: Boolean = false,
                         startMaximized: Boolean = false,
                         hideScrollbars: Boolean = false,
                         incognito: Boolean = false,
                         ignoreCertificateErrors: Boolean = false,
                         autoplayPolicy: Option[String] = None,
                         enableTracing: Boolean = false,
                         enableLogging: Option[String] = None,
                         loggingVerbosity: Option[Int] = None,
                         loggingPath: Option[File] = None,
                         noSandbox: Boolean = false,
                         windowSize: Option[(Int, Int)] = None,
                         forceDeviceScaleFactor: Option[Int] = None,
                         proxyServer: Option[(String, Int)] = None,
                         proxyBypassList: List[String] = Nil,
                         disableFeatures: List[String] = Nil,
                         disablePDFExtension: Boolean = true) {
  private def o(b: Boolean, s: String): List[String] = if (b) {
    List(s)
  } else {
    Nil
  }

  private def om(b: Boolean, list: List[String]): List[String] = if (b) {
    list
  } else {
    Nil
  }

  private def l(name: String, list: List[String]): List[String] = if (list.nonEmpty) {
    List(s"$name=${list.mkString(",")}")
  } else {
    Nil
  }

  lazy val options: List[String] = List(
    o(headless, if (useNewHeadlessMode) "--headless=new" else "--headless"),
    om(disableBackgrounding, List(
      "--disable-background-timer-throttling",
      "--disable-backgrounding-occluded-windows",
      "--disable-renderer-backgrounding"
    )),
    o(disableGPU, "--disable-gpu"),
    o(disableDevSHMUsage, "--disable-dev-shm-usage"),
    o(disableWebSecurity, "--disable-web-security"),
    o(disableBackgroundNetworking, "--disable-background-networking"),
    o(disablePopupBlocking, "--disable-popup-blocking"),
    o(disableSiteIsolationTrials, "--disable-site-isolation-trials"),
    o(disableWebGL, "--disable-webgl"),
    o(disableInfobars, "--disable-infobars"),
    o(disableSync, "--disable-sync"),
    o(disableSoftwareRasterizer, "--disable-software-rasterizer"),
    o(allowRunningInsecureContent, "--allow-running-insecure-content"),
    o(singleProcess, "--single-process"),
    o(disableCache, "--disable-cache"),
    o(enableAutomation, "--enable-automation"),
    o(startMaximized, "--start-maximized"),
    o(hideScrollbars, "--hide-scrollbars"),
    o(incognito, "--incognito"),
    o(ignoreCertificateErrors, "--ignore-certificate-errors"),
    autoplayPolicy.map(p => s"--autoplay-policy=$p").toList,
    o(enableTracing, "--enable-tracing"),
    enableLogging.map(l => s"--enable-logging=$l").toList,
    loggingVerbosity.map(v => s"--v=$v").toList,
    loggingPath.map(p => s"--log-net-log=${p.getAbsolutePath}").toList,
    o(noSandbox, "--no-sandbox"),
    o(disablePDFExtension, "--disable-pdf-extension"),
    windowSize.map { case (w, h) => s"--window-size=$w,$h" }.toList,
    forceDeviceScaleFactor.map(f => s"--force-device-scale-factor=$f").toList,
    List(s"--user-data-dir=${userDataDir.getAbsolutePath}"),
    proxyServer.map { case (host, port) => s"--proxy-server=$host:$port" }.toList,
    l("--proxy-bypass-list", proxyBypassList),
    l("--disable-features", disableFeatures)
  ).flatten

  private[robobrowser] def prepareUserDataDir(): Unit = {
    Files.createDirectories(userDataDir.toPath)
    if (disablePDFExtension) {
      BrowserConfig.ensurePdfPreferences(userDataDir.toPath)
    }
  }
}

object BrowserConfig {
  def resolveDataDir(name: String): File = {
    val f = new File(s"${System.getProperty("user.home")}/.config/robobrowser/$name")
    Files.createDirectories(f.toPath)
    f
  }

  private val DefaultProfileDirectory = "Default"
  private val PreferencesFileName = "Preferences"

  private def ensurePdfPreferences(userDataDir: Path): Unit = synchronized {
    val profileDir = userDataDir.resolve(DefaultProfileDirectory)
    Files.createDirectories(profileDir)
    val preferencesPath = profileDir.resolve(PreferencesFileName)

    val existing = if (Files.exists(preferencesPath)) {
      Try(JsonParser(preferencesPath)).getOrElse(obj())
    } else {
      obj()
    }

    val desired = obj(
      "download" -> obj(
        "open_pdf_in_system_reader" -> true,
        "prompt_for_download" -> false
      ),
      "plugins" -> obj(
        "always_open_pdf_externally" -> true,
        "plugins_disabled" -> arr(str("Chrome PDF Viewer"))
      )
    )

    val merged = existing.merge(desired)
    Files.writeString(preferencesPath, JsonFormatter.Compact(merged))
  }
}