package com.outr.robobrowser.integration

case class IntegrationTest(index: Int,
                           description: String,
                           context: Option[String],
                           function: () => Any,
                           pkg: sourcecode.Pkg,
                           fileName: sourcecode.FileName,
                           name: sourcecode.Name,
                           line: sourcecode.Line) {
  lazy val label: String = context match {
    case Some(c) => s"$c: $description"
    case None => description
  }

  lazy val position: String = s"${pkg.value}.${name.value}:${line.value}"

  override def toString: String = s"$label(index: $index, position: $position)"
}