package com.outr.robobrowser.monitor

import com.outr.robobrowser.RoboBrowser
import reactify.Var

import java.awt.{BorderLayout, Font}
import java.awt.event.{ActionEvent, MouseAdapter, MouseEvent}
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.swing.plaf.nimbus.NimbusLookAndFeel
import javax.swing.{BoxLayout, ImageIcon, JButton, JFrame, JLabel, JPanel, UIManager, WindowConstants}
import scala.concurrent.duration._

class Monitor(val browser: RoboBrowser, updateOnDispose: Boolean = true) {
  val visible: Var[Boolean] = Var(false)
  val refresher: Var[Option[Refresher]] = Var(None)

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
  private lazy val refreshButton = new JButton("Refresh")
  private lazy val panel = new JPanel(new BorderLayout)
  private lazy val label = new JLabel

  init()

  private def init(): Unit = {
    UIManager.setLookAndFeel(new NimbusLookAndFeel)

    if (updateOnDispose) {
      browser.disposing.on {
        if (visible()) {
          refresh()
        }
      }
    }

    viewSourceButton.setFont(Monitor.Normal)
    visualSelectorButton.setFont(Monitor.Normal)
    pauseButton.setFont(Monitor.Normal)
    refreshButton.setFont(Monitor.Normal)

    viewSourceButton.addActionListener((_: ActionEvent) => browser.ignoringPause(htmlViewer.refresh()))
    browser.paused.attachAndFire {
      case true => pauseButton.setText("Resume")
      case false => pauseButton.setText("Pause")
    }
    visualSelectorButton.addActionListener((_: ActionEvent) => visualSelector.setVisible(true))
    pauseButton.addActionListener((_: ActionEvent) => browser.paused @= !browser.paused())
    refreshButton.addActionListener((_: ActionEvent) => refresh())
    label.addMouseListener(new MouseAdapter {
      override def mouseClicked(e: MouseEvent): Unit = {
        visualSelector.clear()
        visualSelector.select(e.getX, e.getY)
      }
    })

    buttons.add(viewSourceButton)
    buttons.add(visualSelectorButton)
    buttons.add(pauseButton)
    buttons.add(refreshButton)
    panel.add(buttons, BorderLayout.NORTH)
    panel.add(label, BorderLayout.CENTER)
    frame.setContentPane(panel)

    visible.attach(frame.setVisible)
  }

  def start(every: FiniteDuration = 1.second): Refresher = {
    val r = new Refresher(every)
    refresher @= Some(r)
    r
  }

  def refreshing[Return](every: FiniteDuration = 1.second)(f: => Return): Return = {
    val r = start(every)
    try {
      val result: Return = f
      result
    } finally {
      r.stop()
    }
  }

  def refresh(): Unit = synchronized {
    val bytes = browser.capture()
    val icon = Monitor.bytes2Icon(bytes)
    label.setIcon(icon)
    frame.pack()
    frame.setLocationRelativeTo(null)
    visible @= true
  }

  def refreshAndPause(): Unit = {
    refresh()
    browser.paused @= true
  }

  def stop(): Unit = refresher @= None

  class Refresher(every: FiniteDuration) {
    private var keepAlive = true

    def start(): Unit = {
      new Thread {
        override def run(): Unit = while (keepAlive) {
          refresh()
          browser.sleep(every)
        }
      }
      Monitor.this.synchronized {
        refresher() match {
          case Some(previous) =>
            scribe.warn("Stopping active refresher and replacing")
            previous.stop()
          case None => // Not set
        }
        refresher @= Some(this)
      }
    }

    def stop(): Unit = {
      keepAlive = false
      refresher @= None
    }
  }
}

object Monitor {
  lazy val Normal = new Font(Font.SANS_SERIF, Font.PLAIN, 18)
  lazy val Mono = new Font(Font.MONOSPACED, Font.PLAIN, 18)

  def bytes2Icon(bytes: Array[Byte]): ImageIcon = {
    val image = ImageIO.read(new ByteArrayInputStream(bytes))
    new ImageIcon(image)
  }
}