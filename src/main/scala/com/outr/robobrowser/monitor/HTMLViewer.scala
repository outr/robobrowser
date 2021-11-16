package com.outr.robobrowser.monitor

import org.fife.ui.rsyntaxtextarea.{RSyntaxTextArea, SyntaxConstants}
import org.fife.ui.rtextarea.{RTextScrollPane, SearchContext, SearchEngine}

import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing._

class HTMLViewer(monitor: Monitor) extends JFrame("View Source Code") {
  val controls = new JPanel
  controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS))
  val searchField = new JTextField(30)
  searchField.setFont(Monitor.Normal)
  searchField.addActionListener((_: ActionEvent) => findNext())
  controls.add(searchField)
  val searchInfo = new JLabel("")
  searchInfo.setFont(Monitor.Normal)
  searchInfo.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10))
  controls.add(searchInfo)
  val searchButton = new JButton("Find Next")
  searchButton.setFont(Monitor.Normal)
  searchButton.addActionListener((_: ActionEvent) => findNext())
  controls.add(searchButton)
  val refreshButton = new JButton("Refresh")
  refreshButton.setFont(Monitor.Normal)
  refreshButton.addActionListener((_: ActionEvent) => refresh())
  controls.add(refreshButton)

  val panel = new JPanel(new BorderLayout)
  val textArea = new RSyntaxTextArea(20, 60)
  textArea.setFont(Monitor.Mono)
  val scrollPane = new RTextScrollPane(textArea)

  textArea.setMarkOccurrences(true)
  textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_HTML)
  textArea.setCodeFoldingEnabled(true)
  panel.add(controls, BorderLayout.NORTH)
  panel.add(scrollPane, BorderLayout.CENTER)

  setContentPane(panel)
  setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)
  setSize(1600, 1200)
  setLocationRelativeTo(null)

  def findNext(): Unit = {
    val text = searchField.getText
    val context = new SearchContext
    context.setSearchFor(text)
    context.setMatchCase(false) // TODO: support
    context.setRegularExpression(false) // TODO: support
    context.setWholeWord(false) // TODO: support
    context.setSearchForward(true)
    context.setSearchWrap(true)
    val result = SearchEngine.find(textArea, context)

    searchInfo.setText(s"${result.getMarkedCount} matches")
  }

  def refresh(): Unit = monitor.browser.ignoringPause {
    textArea.setText(monitor.browser.content)
    textArea.setCaretPosition(0)
    setVisible(true)
  }
}