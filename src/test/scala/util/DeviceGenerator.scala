package util

import fabric.parse.Json
import fabric.rw._
import io.youi.stream.IO

import java.io.File

/**
 * Generating from https://api.browserstack.com/automate/browsers.json
 */
object DeviceGenerator {
  def main(args: Array[String]): Unit = {
    val jsonString = IO.stream(getClass.getClassLoader.getResourceAsStream("browsers.json"), new StringBuilder).toString
    val json = Json.parse(jsonString)
    val info = json.as[List[DeviceInfo]]
    val oses = info.groupBy(_.os)
    android(oses("android"))
    ios(oses("ios"))
  }

  def android(list: List[DeviceInfo]): Unit = {
    val devices = list.groupBy(_.device)
    val objects = devices.map {
      case (key, values) =>
        val versions = values.map { v =>
          s"""    def `v${v.os_version}`: RoboBrowserBuilder[RoboAndroid] = caps(
             |      "os_version" -> "${v.os_version}",
             |      "device" -> "${v.device.get}"
             |    )""".stripMargin
        }
        s"""  object `${key.get}` {
           |${versions.mkString("\n")}
           |  }""".stripMargin
    }.mkString("\n")
    val file = new File("src/main/scala/com/outr/robobrowser/appium/AndroidCapabilities.scala")
    assert(file.isFile, s"File not found: ${file.getAbsolutePath}")
    val code =
      s"""package com.outr.robobrowser.appium
         |
         |import com.outr.robobrowser.{RoboBrowser, RoboBrowserBuilder}
         |
         |class AndroidCapabilities[T <: RoboBrowser](builder: RoboBrowserBuilder[T]) {
         |  private def caps(capabilities: (String, Any)*): RoboBrowserBuilder[RoboAndroid] = builder
         |    .withCapabilities(
         |      "os" -> "android",
         |      "browser" -> "android",
         |      "real_mobile" -> true
         |    )
         |    .withCapabilities(capabilities: _*)
         |    .withCreator(RoboAndroid.create)
         |
         |$objects
         |}""".stripMargin
    IO.stream(code, file)
  }

  def ios(list: List[DeviceInfo]): Unit = {
    val devices = list.groupBy(_.device)
    val objects = devices.map {
      case (key, values) =>
        val versions = values.map { v =>
          s"""    def `v${v.os_version}`: RoboBrowserBuilder[RoboIOS] = caps(
             |      "os_version" -> "${v.os_version}",
             |      "device" -> "${v.device.get}"
             |    )""".stripMargin
        }
        s"""  object `${key.get}` {
           |${versions.mkString("\n")}
           |  }""".stripMargin
    }.mkString("\n")
    val file = new File("src/main/scala/com/outr/robobrowser/appium/IOSCapabilities.scala")
    assert(file.isFile, s"File not found: ${file.getAbsolutePath}")
    val code =
      s"""package com.outr.robobrowser.appium
         |
         |import com.outr.robobrowser.{RoboBrowser, RoboBrowserBuilder}
         |
         |class IOSCapabilities[T <: RoboBrowser](builder: RoboBrowserBuilder[T]) {
         |  private def caps(capabilities: (String, Any)*): RoboBrowserBuilder[RoboIOS] = builder
         |    .withCapabilities(
         |      "os" -> "ios",
         |      "browser" -> "iphone",
         |      "real_mobile" -> true
         |    )
         |    .withCapabilities(capabilities: _*)
         |    .withCreator(RoboIOS.create)
         |
         |$objects
         |}""".stripMargin
    IO.stream(code, file)
  }

  case class DeviceInfo(os: String,
                        os_version: String,
                        browser: String,
                        device: Option[String],
                        browser_version: Option[String],
                        real_mobile: Option[Boolean])

  object DeviceInfo {
    implicit val rw: ReaderWriter[DeviceInfo] = ccRW
  }
}