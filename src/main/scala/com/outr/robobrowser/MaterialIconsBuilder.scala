package com.outr.robobrowser

import java.io.{BufferedWriter, FileOutputStream, OutputStreamWriter}

import io.youi.net._
import org.openqa.selenium.By

object MaterialIconsBuilder {
  def main(args: Array[String]): Unit = {
    val browser = new RoboBrowser
    browser.load(url"https://material.io/tools/icons/?style=baseline")
    val writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("Icons.txt")))
    writer.write("object Icons {\n")
    browser.by(By.tagName("icons-category")).foreach { categoryContainer =>
      val category = categoryContainer.by(By.className("category-name")).head.text
      writer.write(s"  object $category {\n")
      categoryContainer.by(By.className("icon-item-container")).map(_.attribute("title")).foreach { title =>
        val camelCase = "_([a-z0-9])".r.replaceAllIn(title, m => {
          m.group(1).toUpperCase
        }).capitalize match {
          case s if s.charAt(0).isDigit => s"`$s`"
          case s => s
        }
        writer.write(s"""    lazy val $camelCase = MaterialIcon("$title")\n""")
      }
      writer.write("  }\n")
    }
    writer.write("}")
    writer.flush()
    writer.close()
    browser.dispose()
  }
}