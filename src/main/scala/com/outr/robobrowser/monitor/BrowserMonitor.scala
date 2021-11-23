package com.outr.robobrowser.monitor

import com.outr.robobrowser.{Context, RoboBrowser}
import reactify.Var

import java.awt.{BorderLayout, Dimension, Image}
import java.awt.event.{ActionEvent, MouseAdapter, MouseEvent}
import javax.swing.plaf.nimbus.NimbusLookAndFeel
import javax.swing.{BorderFactory, BoxLayout, ImageIcon, JComboBox, JFrame, JLabel, JPanel, JScrollPane, UIManager, WindowConstants}
import scala.concurrent.duration._
import scala.util.Try

class BrowserMonitor(val browser: RoboBrowser, updateOnDispose: Boolean = true) {
  val visible: Var[Boolean] = Var(false)
  val refresher: Var[Option[Refresher]] = Var(None)

  private lazy val visualSelector = new VisualSelector(this)
  private lazy val htmlViewer = new HTMLViewer(this)
  private lazy val executor = new JavaScriptExecutor(this)

  private lazy val frame = {
    val f = new JFrame("RoboBrowser Monitor")
    f.setMinimumSize(new Dimension(1100, 600))
    f.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)
    f
  }

  private lazy val buttons = {
    val p = new JPanel
    p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS))
    p
  }
  private lazy val viewSourceButton = button("View Source") {
    browser.ignoringPause(htmlViewer.refresh())
  }
  private lazy val visualSelectorButton = button("Visual Selector") {
    visualSelector.setVisible(true)
  }
  private lazy val executeButton = button("Execute") {
    executor.setVisible(true)
  }
  private lazy val contextSelect = new JComboBox[Context](Array(browser.initialContext, Context.Native))
  private lazy val pauseButton = button("Pause") {
    browser.paused @= !browser.paused()
  }
  private lazy val refreshButton = button("Refresh") {
    refresh()
  }
  private lazy val lastRefreshed = new JLabel("Not Refreshed")
  private lazy val panel = new JPanel(new BorderLayout)
  private lazy val label = new JLabel
  private lazy val scrollPane = new JScrollPane(label)

  private var lastRefreshedTime: Long = 0L

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

    val t = new Thread {
      setDaemon(true)

      override def run(): Unit = while (true) {
        if (lastRefreshedTime > 0L) {
          val elapsed = (System.currentTimeMillis() - lastRefreshedTime) / 1000
          lastRefreshed.setText(s"Last Refreshed: $elapsed seconds ago")
        }
        Thread.sleep(1000)
      }
    }
    t.start()

    lastRefreshed.setFont(font.Normal)

    lastRefreshed.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0))
    browser.paused.attachAndFire {
      case true => pauseButton.setText("Resume")
      case false => pauseButton.setText("Pause")
    }
    label.addMouseListener(new MouseAdapter {
      override def mouseClicked(e: MouseEvent): Unit = if (browser.context() != Context.Native) {
        visualSelector.clear()
        visualSelector.select(e.getX, e.getY)
      }
    })
    contextSelect.setFont(font.Normal)

    buttons.add(viewSourceButton)
    buttons.add(visualSelectorButton)
    buttons.add(executeButton)
    buttons.add(contextSelect)
    buttons.add(pauseButton)
    buttons.add(refreshButton)
    buttons.add(lastRefreshed)
    panel.add(buttons, BorderLayout.NORTH)
    panel.add(scrollPane, BorderLayout.CENTER)
    frame.setContentPane(panel)

    visible.attach(frame.setVisible)
  }

  def context: Context = contextSelect.getSelectedItem.asInstanceOf[Context]

  def start(every: FiniteDuration = 1.second): Refresher = {
    val r = new Refresher(every)
    r.start()
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
    browser.ignoringPause {
      val bytes = browser.capture()
      val icon = bytes2Icon(bytes)
      val image = icon.getImage
      val scaled = if (browser.context() != Context.Native) {
        val (width, height) = browser.size
        image.getScaledInstance(width, height, Image.SCALE_SMOOTH)
      } else {
        image
      }
      label.setIcon(new ImageIcon(scaled))
      frame.pack()
      frame.setLocationRelativeTo(null)
      lastRefreshedTime = System.currentTimeMillis()
      visible @= true
    }
  }

  def refreshAndPause(blockCurrentThread: Boolean = true): Unit = {
    refresh()
    browser.paused @= true
    if (blockCurrentThread) {
      while (browser.paused()) {
        browser.sleep(500.millis)
      }
    }
  }

  def stop(): Unit = refresher @= None

  class Refresher(every: FiniteDuration) {
    private var keepAlive = true

    def start(): Unit = {
      new Thread {
        override def run(): Unit = while (keepAlive) {
          Try(refresh()).failed.foreach { t =>
            scribe.warn(s"Error while refreshing in Refresher", t)
          }
          browser.sleep(every)
        }
      }.start()
      BrowserMonitor.this.synchronized {
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