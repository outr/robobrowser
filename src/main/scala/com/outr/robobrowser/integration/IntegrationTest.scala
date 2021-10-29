package com.outr.robobrowser.integration

case class IntegrationTest(index: Int,
                           description: String,
                           context: Option[String],
                           function: () => Any,
                           pkg: sourcecode.Pkg,
                           fileName: sourcecode.FileName,
                           name: sourcecode.Name,
                           line: sourcecode.Line)