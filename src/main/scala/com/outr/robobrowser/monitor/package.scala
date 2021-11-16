package com.outr.robobrowser

import javax.swing.SwingUtilities

package object monitor {
  def gui(f: => Unit): Unit = SwingUtilities.invokeLater(() => f)
}