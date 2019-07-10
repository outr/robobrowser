package com.outr.robobrowser

import java.io.{BufferedWriter, FileOutputStream, OutputStreamWriter}

import io.youi.net._
import org.openqa.selenium.By

object FontAwesomeBuilder {
  def main(args: Array[String]): Unit = {
    val browser = new RoboBrowser
    browser.load(url"https://fontawesome.com/cheatsheet/free/brands")
    val writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("Awesome.txt")))
    browser.by(By.id("brands")).head.by(By.tagName("article")).foreach { article =>
      val id = article.attribute("id")
      val camelCase = "[_-]([a-z0-9])".r.replaceAllIn(id, m => {
        m.group(1).toUpperCase
      }).capitalize match {
        case s if s.charAt(0).isDigit => s"`$s`"
        case s => s
      }
      writer.write(s"""lazy val $camelCase = FontAwesomeIcon("$id")\n""")
    }
    writer.flush()
    writer.close()
    browser.dispose()
  }
}
