package com.outr.robobrowser

import org.openqa.selenium
import org.openqa.selenium.interactions.Actions

import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

class ActionBuilder(browser: RoboBrowser) {
  private implicit def we2Swe(element: WebElement): selenium.WebElement = SeleniumWebElement.underlying(element)
  private implicit def a2Ab(actions: Actions): ActionBuilder = this

  private val actions = browser.withDriver(driver => new Actions(driver))

  def sendKeys(keys: CharSequence*): ActionBuilder = actions.sendKeys(keys*)

  def sendKeys(element: WebElement, keys: CharSequence*): ActionBuilder = actions.sendKeys(element, keys*)

  def click(): ActionBuilder = actions.click()

  def click(element: WebElement): ActionBuilder = actions.click(element)

  def clickAndHold(): ActionBuilder = actions.clickAndHold()

  def clickAndHold(element: WebElement): ActionBuilder = actions.clickAndHold(element)

  def contextClick(): ActionBuilder = actions.contextClick()

  def contextClick(element: WebElement): ActionBuilder = actions.contextClick(element)

  def doubleClick(): ActionBuilder = actions.doubleClick()

  def doubleClick(element: WebElement): ActionBuilder = actions.doubleClick(element)

  def dragAndDrop(source: WebElement, target: WebElement): ActionBuilder = actions.dragAndDrop(source, target)

  def dragAndDropBy(source: WebElement, xOffset: Int, yOffset: Int): ActionBuilder =
    actions.dragAndDropBy(source, xOffset, yOffset)

  def keyDown(key: CharSequence): ActionBuilder = actions.keyDown(key)

  def keyDown(target: WebElement, key: CharSequence): ActionBuilder = actions.keyDown(target, key)

  def keyUp(key: CharSequence): ActionBuilder = actions.keyUp(key)

  def keyUp(target: WebElement, key: CharSequence): ActionBuilder = actions.keyUp(target, key)

  def moveByOffset(xOffset: Int, yOffset: Int): ActionBuilder = actions.moveByOffset(xOffset, yOffset)

  def moveToElement(target: WebElement): ActionBuilder = actions.moveToElement(target)

  def moveToElement(target: WebElement, xOffset: Int, yOffset: Int): ActionBuilder =
    actions.moveToElement(target, xOffset, yOffset)

  def pause(duration: FiniteDuration): ActionBuilder = actions.pause(duration.toMillis)

  def release(): ActionBuilder = actions.release()

  def release(target: WebElement): ActionBuilder = actions.release(target)

  def scrollByAmount(deltaX: Int, deltaY: Int): ActionBuilder = actions.scrollByAmount(deltaX, deltaY)

  def scrollToElement(element: WebElement): ActionBuilder = actions.scrollToElement(element)

  def perform(): Unit = actions.perform()
}