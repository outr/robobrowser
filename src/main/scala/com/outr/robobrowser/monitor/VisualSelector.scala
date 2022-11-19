package com.outr.robobrowser.monitor

import com.outr.robobrowser.monitor.font.Normal
import com.outr.robobrowser.{By, ByType, WebElement}

import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing._
import scala.util.Try

class VisualSelector(monitor: BrowserMonitor) extends JFrame("Visual Selector") {
  val controls = new JPanel
  controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS))
  private val byType = {
    val c = new JComboBox[ByType](ByType.all.toArray)
    c.setFont(Normal)
    c
  }
  controls.add(byType)
  val selectorInput = new JTextField(30)
  selectorInput.setFont(Normal)
  selectorInput.addActionListener((_: ActionEvent) => query())
  controls.add(selectorInput)
  val selectorResults = new JLabel("")
  selectorResults.setFont(Normal)
  selectorResults.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10))
  controls.add(selectorResults)
  private val queryButton = button("Query") {
    query()
  }
  controls.add(queryButton)
  private val highlightButton = button("Highlight") {
    highlight()
  }
  controls.add(highlightButton)
  private val clearButton = button("Clear") {
    clear()
    clearHighlight()
  }
  controls.add(clearButton)

  val panel = new JPanel(new BorderLayout)
  panel.add(controls, BorderLayout.NORTH)

  val results = new JPanel
  results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS))
  val scrollPane = new JScrollPane(results)
  scrollPane.getVerticalScrollBar.setUnitIncrement(16)
  panel.add(scrollPane, BorderLayout.CENTER)

  private var selected = List.empty[WebElement]
  private var highlighted = List.empty[WebElement]

  setContentPane(panel)
  setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)
  setSize(800, 600)
  setLocationRelativeTo(null)

  def query(): Unit = if (selectorInput.getText.trim.nonEmpty) {
    monitor.browser.ignoringPause {
      val byType = this.byType.getSelectedItem.asInstanceOf[ByType]
      val by = By(selectorInput.getText, byType, monitor.context)
      val results = monitor.browser.by(by)
      selectorResults.setText(s"${results.length} results")

      clear()
      results.foreach(select)
      scrollPane.getVerticalScrollBar.setValue(0)
    }
  }

  def select(element: WebElement): Unit = {
    val c = SelectorResult(monitor, element)
    results.add(c)
    synchronized {
      selected = element :: selected
    }
    setVisible(true)
  }

  def select(x: Int, y: Int): Unit = monitor.browser.ignoringPause {
    monitor.browser.atPoint(x, y) match {
      case Some(element) => select(element)
      case None => scribe.warn(s"Nothing found at $x x $y")
    }
  }

  def highlight(element: WebElement): Unit = monitor.browser.ignoringPause {
    element.attribute("rb-border-width", element.style("borderWidth"))
    element.attribute("rb-border-style", element.style("borderStyle"))
    element.attribute("rb-border-color", element.style("borderColor"))
    element
      .style("borderWidth", 5)
      .style("borderStyle", "solid")
      .style("borderColor", "red")
    synchronized {
      highlighted = element :: highlighted
    }
    monitor.refresh()
  }

  def highlight(): Unit = if (selectorInput.getText.trim.nonEmpty) {
    monitor.browser.ignoringPause {
      val results = monitor.browser.by(By.css(selectorInput.getText, monitor.context))
      selectorResults.setText(s"${results.length} results")

      clearHighlight()
      results.foreach(highlight)
    }
  } else {      // Highlight current selection
    monitor.browser.ignoringPause {
      clearHighlight()
      selected.foreach(highlight)
    }
  }

  def clear(): Unit = synchronized {
    results.removeAll()
    selected = Nil
    gui(results.repaint())
  }

  def clearHighlight(): Unit = synchronized {
    monitor.browser.ignoringPause {
      highlighted.foreach { element =>
        Try {
          element.style("borderWidth", element.attribute("rb-border-width"))
          element.style("borderStyle", element.attribute("rb-border-style"))
          element.style("borderColor", element.attribute("rb-border-color"))
        }
      }
    }
    highlighted = Nil
    monitor.browser.ignoringPause(monitor.refresh())
  }
}