package com.outr.robobrowser.monitor

import com.outr.robobrowser.RoboBrowser

import java.awt.{BorderLayout, Font}
import java.awt.event.ActionEvent
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.swing.plaf.nimbus.NimbusLookAndFeel
import javax.swing.{BoxLayout, ImageIcon, JButton, JFrame, JLabel, JPanel, UIManager, WindowConstants}
import scala.concurrent.duration._

class Monitor(val browser: RoboBrowser) {
  private var running = false

  private lazy val visualSelector = new VisualSelector(this)
  private lazy val htmlViewer = new HTMLViewer(this)

  private lazy val frame = {
    val f = new JFrame("RoboBrowser Monitor")
    f.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)
    f
  }

  private lazy val buttons = {
    val p = new JPanel
    p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS))
    p
  }
  private lazy val viewSourceButton = new JButton("View Source")
  private lazy val visualSelectorButton = new JButton("Visual Selector")
  private lazy val pauseButton = new JButton("Pause")
  private lazy val panel = new JPanel(new BorderLayout)
  private lazy val label = new JLabel

  init()

  private def init(): Unit = {
    UIManager.setLookAndFeel(new NimbusLookAndFeel)

    viewSourceButton.setFont(Monitor.Normal)
    visualSelectorButton.setFont(Monitor.Normal)
    pauseButton.setFont(Monitor.Normal)

    viewSourceButton.addActionListener((_: ActionEvent) => browser.ignoringPause(htmlViewer.refresh()))
    browser.paused.attachAndFire {
      case true => pauseButton.setText("Resume")
      case false => pauseButton.setText("Pause")
    }
    visualSelectorButton.addActionListener((_: ActionEvent) => visualSelector.setVisible(true))
    pauseButton.addActionListener((_: ActionEvent) => browser.paused @= !browser.paused())
    buttons.add(viewSourceButton)
    buttons.add(visualSelectorButton)
    buttons.add(pauseButton)
    panel.add(buttons, BorderLayout.NORTH)
    panel.add(label, BorderLayout.CENTER)
    frame.setContentPane(panel)
  }

  def start(every: FiniteDuration = 1.second): Unit = run(every)

  protected def run(every: FiniteDuration): Unit = {
    refresh()
    if (!running) {
      // Stop
    } else {
      browser.sleep(every)
      run(every)
    }
  }

  def refresh(): Unit = synchronized {
    val bytes = browser.capture()
    val icon = Monitor.bytes2Icon(bytes)
    label.setIcon(icon)
    frame.pack()
    frame.setLocationRelativeTo(null)
    frame.setVisible(true)
  }

  def refreshAndPause(): Unit = {
    refresh()
    browser.paused @= true
  }

  def stop(): Unit = running = false
}

object Monitor {
  lazy val Normal = new Font(Font.SANS_SERIF, Font.PLAIN, 18)
  lazy val Mono = new Font(Font.MONOSPACED, Font.PLAIN, 18)

  def bytes2Icon(bytes: Array[Byte]): ImageIcon = {
    val image = ImageIO.read(new ByteArrayInputStream(bytes))
    new ImageIcon(image)
  }
}