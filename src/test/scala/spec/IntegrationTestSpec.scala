package spec

import com.outr.robobrowser.{Browser, BrowserStackOptions, MobileBrowser, RoboBrowser}
import com.outr.robobrowser.integration.{IntegrationTestSuite, IntegrationTestsInstance}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import profig.Profig

class IntegrationTestSpec extends AnyWordSpec with Matchers with IntegrationTestSuite {
  lazy val bsOptions: BrowserStackOptions = BrowserStackOptions(
    Profig("browserStackUsername").as[String],
    Profig("browserStackAutomateKey").as[String],
    "RoboBrowser",
    "IntegrationTestSpec"
  )

  "BrowserStack tests" should {
    "initialize configuration" in {
      Profig.initConfiguration()
    }
    "run successfully on Android" in {
      test on List(
        RoboBrowser.`iPhone 12 Pro`.`v14`
      ).map { builder =>
        IntegrationTestsInstance[MobileBrowser](() => BrowserStackTests(
          label = builder.typed[String]("device"),
          browser = builder.browserStack(bsOptions).create()
        ))
      }

      val failures = run()
      failures should be(Nil)
    }
  }
}