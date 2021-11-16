package com.outr.robobrowser.monitor

import com.outr.robobrowser.WebElement
import org.openqa.selenium.By

import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing._
import scala.util.Try

class VisualSelector(monitor: Monitor) extends JFrame("Visual Selector") {
  val controls = new JPanel
  controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS))
  val selectorInput = new JTextField(30)
  selectorInput.setFont(Monitor.Normal)
  selectorInput.addActionListener((_: ActionEvent) => query())
  controls.add(selectorInput)
  val selectorResults = new JLabel("")
  selectorResults.setFont(Monitor.Normal)
  selectorResults.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10))
  controls.add(selectorResults)
  val queryButton = new JButton("Query")
  queryButton.setFont(Monitor.Normal)
  queryButton.addActionListener((_: ActionEvent) => query())
  controls.add(queryButton)
  val highlightButton = new JButton("Highlight")
  highlightButton.setFont(Monitor.Normal)
  highlightButton.addActionListener((_: ActionEvent) => highlight())
  controls.add(highlightButton)
  val clearButton = new JButton("Clear")
  clearButton.setFont(Monitor.Normal)
  clearButton.addActionListener((_: ActionEvent) => {
    clear()
    clearHighlight()
  })
  controls.add(clearButton)

  val panel = new JPanel(new BorderLayout)
  panel.add(controls, BorderLayout.NORTH)

  val results = new JPanel
  results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS))
  val scrollPane = new JScrollPane(results)
  scrollPane.getVerticalScrollBar.setUnitIncrement(16)
  panel.add(scrollPane, BorderLayout.CENTER)

  private var highlighted = List.empty[WebElement]

  setContentPane(panel)
  setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)
  setSize(800, 600)
  setLocationRelativeTo(null)

  def query(): Unit = if (selectorInput.getText.trim.nonEmpty) {
    monitor.browser.ignoringPause {
      val results = monitor.browser.by(By.cssSelector(selectorInput.getText))
      selectorResults.setText(s"${results.length} results")

      clear()
      results.foreach(select)
      scrollPane.getVerticalScrollBar.setValue(0)
    }
  }

  // TODO: highlight at point, highlight from query in Monitor screenshot
  def select(element: WebElement): Unit = {
    val c = new SelectorResult(element)
    results.add(c)
    setVisible(true)
  }

  def select(x: Int, y: Int): Unit = monitor.browser.ignoringPause {
    val element = monitor.browser.atPoint(x, y)
    select(element)
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

  def highlight(): Unit = monitor.browser.ignoringPause {
    val results = monitor.browser.by(By.cssSelector(selectorInput.getText))
    selectorResults.setText(s"${results.length} results")

    clearHighlight()
    results.foreach(highlight)
  }

  def clear(): Unit = {
    results.removeAll()
    gui(results.repaint())
  }

  def clearHighlight(): Unit = synchronized {
    highlighted.foreach { element =>
      Try {
        element.style("borderWidth", element.attribute("rb-border-width"))
        element.style("borderStyle", element.attribute("rb-border-style"))
        element.style("borderColor", element.attribute("rb-border-color"))
      }
    }
    highlighted = Nil
    monitor.browser.ignoringPause(monitor.refresh())
  }
}