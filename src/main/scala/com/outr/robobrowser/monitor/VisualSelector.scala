package com.outr.robobrowser.monitor

import org.openqa.selenium.By

import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing._

class VisualSelector(monitor: Monitor) extends JFrame("Visual Selector") {
  val controls = new JToolBar
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

  val panel = new JPanel(new BorderLayout)
  panel.add(controls, BorderLayout.NORTH)

  val results = new JPanel
  results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS))
  val scrollPane = new JScrollPane(results)
  scrollPane.getVerticalScrollBar.setUnitIncrement(16)
  panel.add(scrollPane, BorderLayout.CENTER)

  setContentPane(panel)
  setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)
  setSize(800, 600)
  setLocationRelativeTo(null)

  def query(): Unit = if (selectorInput.getText.trim.nonEmpty) {
    monitor.browser.ignoringPause {
      val results = monitor.browser.by(By.cssSelector(selectorInput.getText))
      selectorResults.setText(s"${results.length} results")

      this.results.removeAll()
      results.foreach { element =>
        val c = new SelectorResult(element)
        this.results.add(c)
      }
      SwingUtilities.invokeLater(() => scrollPane.getVerticalScrollBar.setValue(0))
    }
  }
}