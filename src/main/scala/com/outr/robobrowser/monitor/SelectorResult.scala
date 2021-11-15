package com.outr.robobrowser.monitor

import com.outr.robobrowser.WebElement

import java.awt.Color
import javax.swing._
import scala.util.{Failure, Success, Try}

class SelectorResult(element: WebElement) extends JPanel {
  private val icon = Try(Monitor.bytes2Icon(element.capture()))
  private val image = icon match {
    case Success(i) => new JLabel(i)
    case Failure(t) =>
      scribe.warn(t.getMessage)
      val l = new JLabel("Error capturing image", UIManager.getIcon("OptionPane.errorIcon"), SwingConstants.CENTER)
      l.setFont(Monitor.Normal)
      l
  }
  private val label = new JTextArea(element.outerHTML)

  label.setBorder(null)
  label.setLineWrap(true)
  label.setBackground(new Color(0, 0, 0, 0))
  label.setFont(Monitor.Mono)
  label.setOpaque(false)
  setBorder(BorderFactory.createCompoundBorder(
    BorderFactory.createCompoundBorder(
      BorderFactory.createEmptyBorder(5, 5, 5, 5),
      BorderFactory.createLineBorder(Color.black)
    ),
    BorderFactory.createEmptyBorder(5, 5, 5, 5)
  ))
  setLayout(new BoxLayout(this, BoxLayout.Y_AXIS))
  val b = Box.createHorizontalBox()
  b.add(image)
  b.add(Box.createHorizontalGlue())
  add(b)
  add(label)
}