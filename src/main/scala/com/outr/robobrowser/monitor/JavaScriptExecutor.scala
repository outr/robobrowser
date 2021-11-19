package com.outr.robobrowser.monitor

import org.fife.ui.rsyntaxtextarea.{RSyntaxTextArea, SyntaxConstants}
import org.fife.ui.rtextarea.RTextScrollPane

import java.awt.BorderLayout
import javax.swing.{BoxLayout, JFrame, JPanel, WindowConstants}

class JavaScriptExecutor(monitor: Monitor) extends JFrame("JavaScript Executor") {
  private def browser = monitor.browser

  private val controls = new JPanel
  controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS))
  private val executeButton = button("Execute") {
    execute()
  }
  controls.add(executeButton)

  private val panel = new JPanel(new BorderLayout)
  private val textArea = new RSyntaxTextArea(20, 60)
  textArea.setFont(font.Mono)
  textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT)
  textArea.setCodeFoldingEnabled(true)
  val scrollPane = new RTextScrollPane(textArea)
  panel.add(controls, BorderLayout.NORTH)
  panel.add(scrollPane, BorderLayout.CENTER)

  setContentPane(panel)
  setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)
  setSize(1600, 1200)
  setLocationRelativeTo(null)

  def execute(): Unit = browser.ignoringPause {
    val code = textArea.getText
    if (code.trim.nonEmpty) {
      scribe.info(s"Executing: $code")
      val result = browser.execute(code)
      scribe.info(s"Result: $result")
    }
  }
}