package util

import fabric.parse.Json
import fabric.rw._
import io.youi.stream.IO

/**
 * Generating from https://api.browserstack.com/automate/browsers.json
 */
object DeviceGenerator {
  def main(args: Array[String]): Unit = {
    val jsonString = IO.stream(getClass.getClassLoader.getResourceAsStream("browsers.json"), new StringBuilder).toString
    val json = Json.parse(jsonString)
    val info = json.as[List[DeviceInfo]]
    scribe.info(s"Entries: ${info.length}")
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