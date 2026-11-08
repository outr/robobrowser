package spec

import robobrowser.BrowserHost
import java.nio.file.Path

/** Child process for the abrupt-owner recovery regression; never attaches to an existing browser. */
object MirrorOrphanHost {
  def main(args:Array[String]):Unit={
    BrowserHost.start(profile=Some(Path.of(args(0)))).sync()
    new java.util.concurrent.CountDownLatch(1).await()
  }
}
