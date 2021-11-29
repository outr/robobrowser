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
        RoboBrowser.`iPhone XS`.v15,
        RoboBrowser.`iPhone 12 Pro`.`v14`,
        RoboBrowser.`iPhone 11 Pro`.`v13`
//        RoboBrowser.`Samsung Galaxy S21 Ultra`.`v11.0`
      ).map { builder =>
        val browser = builder.browserStack(bsOptions).create()
        browser.verifyWindowInitializationCheck = false
        IntegrationTestsInstance[MobileBrowser](() => BrowserStackTests(
          label = builder.typed[String]("device"),
          browser = browser
        ))
      }

      val failures = run()
      failures should be(Nil)
    }
  }

  override def stopOnAnyFailure: Boolean = true
  override def retries: Int = 0
}