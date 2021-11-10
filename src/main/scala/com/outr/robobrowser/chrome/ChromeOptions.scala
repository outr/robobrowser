//package com.outr.robobrowser.chrome
//
//import com.outr.robobrowser.{BrowserOptions, Device}
//import org.openqa.selenium.chrome
//
//import java.util
//
//case class ChromeOptions(headless: Boolean = true,
//                         device: Device = Device.Chrome,
//                         fakeMedia: Boolean = true,
//                         driverPath: String = "/usr/bin/chromedriver",
//                         notifications: Notifications = Notifications.Default) extends BrowserOptions {
//  override def toCapabilities: chrome.ChromeOptions = {
//    val caps = super.toCapabilities
//    val n = notifications match {
//      case Notifications.Default => 0
//      case Notifications.Allow => 1
//      case Notifications.Block => 2
//    }
//    val contentSettings = new util.HashMap[String, AnyRef]
//    contentSettings.put("notifications", Integer.valueOf(n))
//    val profile = new util.HashMap[String, AnyRef]
//    profile.put("managed_default_content_settings", contentSettings)
//    val prefs = new util.HashMap[String, AnyRef]
//    prefs.put("profile", profile)
//    caps.setExperimentalOption("prefs", prefs)
//    caps
//  }
//}
//
//sealed trait Notifications
//
//object Notifications {
//  case object Default extends Notifications
//  case object Allow extends Notifications
//  case object Block extends Notifications
//}