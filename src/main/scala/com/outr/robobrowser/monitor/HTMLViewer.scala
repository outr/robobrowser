package com.outr.robobrowser.monitor

import com.outr.robobrowser.Context
import org.fife.ui.rsyntaxtextarea.{RSyntaxTextArea, SyntaxConstants}
import org.fife.ui.rtextarea.{RTextScrollPane, SearchContext, SearchEngine}

import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing._

class HTMLViewer(monitor: Monitor) extends JFrame("View Source Code") {
  val controls = new JPanel
  controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS))
  val searchField = new JTextField(30)
  searchField.setFont(font.Normal)
  searchField.addActionListener((_: ActionEvent) => findNext())
  controls.add(searchField)
  val searchInfo = new JLabel("")
  searchInfo.setFont(font.Normal)
  searchInfo.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10))
  controls.add(searchInfo)
  private val searchButton = button("Find Next") {
    findNext()
  }
  controls.add(searchButton)
  private val refreshButton = button("Refresh") {
    refresh()
  }
  controls.add(refreshButton)

  val panel = new JPanel(new BorderLayout)
  val textArea = new RSyntaxTextArea(20, 60)
  textArea.setFont(font.Mono)
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
    val content = monitor.browser.content(monitor.context)
    textArea.setText(content)
    textArea.setCaretPosition(0)
    setVisible(true)
  }
}