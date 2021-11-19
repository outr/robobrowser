package com.outr.robobrowser.monitor

import com.outr.robobrowser.WebElement

import java.awt.Color
import javax.swing._
import scala.util.{Failure, Success, Try}

case class SelectorResult(monitor: Monitor, element: WebElement) extends JPanel {
  private def browser = monitor.browser
  private val icon = Try(bytes2Icon(element.capture()))
  private val image = icon match {
    case Success(i) => new JLabel(i)
    case Failure(t) =>
      scribe.warn(t.getMessage)
      val l = new JLabel("Error capturing image", UIManager.getIcon("OptionPane.errorIcon"), SwingConstants.CENTER)
      l.setFont(font.Normal)
      l
  }
  private val label = new JTextArea(element.toString)
  private val controls = {
    val p = new JPanel
    p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS))
    p
  }
  private val clickButton = button("Click") {
    browser.ignoringPause {
      element.click()
      monitor.refresh()
    }
  }

  label.setBorder(null)
  label.setLineWrap(true)
  label.setBackground(new Color(0, 0, 0, 0))
  label.setFont(font.Mono)
  label.setOpaque(false)
  setBorder(BorderFactory.createCompoundBorder(
    BorderFactory.createCompoundBorder(
      BorderFactory.createEmptyBorder(5, 5, 5, 5),
      BorderFactory.createLineBorder(Color.black)
    ),
    BorderFactory.createEmptyBorder(5, 5, 5, 5)
  ))
  setLayout(new BoxLayout(this, BoxLayout.Y_AXIS))
  add(box.left(image))
  add(label)

  controls.add(clickButton)

  add(controls)
}