package com.outr.robobrowser

import org.openqa.selenium.{JavascriptExecutor, WebDriver}
import scala.jdk.CollectionConverters._

case class WebStorageUtil(driver: WebDriver) {
  private val jsExecutor = driver.asInstanceOf[JavascriptExecutor]

  // Set an item in localStorage
  def setLocalStorageItem(key: String, value: String): Unit = {
    jsExecutor.executeScript(s"window.localStorage.setItem('$key','$value');")
  }

  // Get an item from localStorage
  def getLocalStorageItem(key: String): Option[String] = {
    Option(jsExecutor.executeScript(s"return window.localStorage.getItem('$key');").asInstanceOf[String])
  }

  // Remove an item from localStorage
  def removeLocalStorageItem(key: String): Unit = {
    jsExecutor.executeScript(s"window.localStorage.removeItem('$key');")
  }

  // Clear all items from localStorage
  def clearLocalStorage(): Unit = {
    jsExecutor.executeScript("window.localStorage.clear();")
  }

  // Get all keys from localStorage
  def localStorageKeys: Set[String] = {
    val keys = jsExecutor.executeScript(
      "return Object.keys(window.localStorage);"
    ).asInstanceOf[java.util.List[String]]
    keys.asScala.toSet
  }

  // Set an item in sessionStorage
  def setSessionStorageItem(key: String, value: String): Unit = {
    jsExecutor.executeScript(s"window.sessionStorage.setItem('$key','$value');")
  }

  // Get an item from sessionStorage
  def getSessionStorageItem(key: String): Option[String] = {
    Option(jsExecutor.executeScript(s"return window.sessionStorage.getItem('$key');").asInstanceOf[String])
  }

  // Remove an item from sessionStorage
  def removeSessionStorageItem(key: String): Unit = {
    jsExecutor.executeScript(s"window.sessionStorage.removeItem('$key');")
  }

  // Clear all items from sessionStorage
  def clearSessionStorage(): Unit = {
    jsExecutor.executeScript("window.sessionStorage.clear();")
  }

  // Get all keys from sessionStorage
  def sessionStorageKeys: Set[String] = {
    val keys = jsExecutor.executeScript(
      "return Object.keys(window.sessionStorage);"
    ).asInstanceOf[java.util.List[String]]
    keys.asScala.toSet
  }
}