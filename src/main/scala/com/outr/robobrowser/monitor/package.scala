package com.outr.robobrowser

import java.awt.{Component, Font}
import java.awt.event.ActionEvent
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.swing.{Box, ImageIcon, JButton, SwingUtilities}

package object monitor {
  object font {
    lazy val Normal = new Font(Font.SANS_SERIF, Font.PLAIN, 18)
    lazy val Mono = new Font(Font.MONOSPACED, Font.PLAIN, 18)
  }

  def gui(f: => Unit): Unit = SwingUtilities.invokeLater(() => f)

  def button(label: String)(action: => Unit): JButton = {
    val b = new JButton(label)
    b.setFont(font.Normal)
    b.addActionListener((_: ActionEvent) => action)
    b
  }

  def bytes2Icon(bytes: Array[Byte]): ImageIcon = {
    val image = ImageIO.read(new ByteArrayInputStream(bytes))
    new ImageIcon(image)
  }

  object box {
    def left(component: Component): Component = {
      val b = Box.createHorizontalBox()
      b.add(component)
      b.add(Box.createHorizontalGlue())
      b
    }
  }
}