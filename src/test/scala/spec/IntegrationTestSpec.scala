package spec

import com.outr.robobrowser.browser.ios.IOS
import com.outr.robobrowser.MobileBrowser
import com.outr.robobrowser.integration.{IntegrationTestSuite, IntegrationTestsInstance}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import profig.Profig

import com.outr.robobrowser.browserstack._

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
        IOS.`iPhone XS`.v15,
        IOS.`iPhone 12 Pro`.`v14`,
        IOS.`iPhone 11 Pro`.`v13`
//        Android.`Samsung Galaxy S21 Ultra`.`v11.0`
      ).map { options =>
        val browser = options.browserStack(bsOptions).create()
        browser.verifyWindowInitializationCheck = false
        IntegrationTestsInstance[MobileBrowser](() => BrowserStackTests(
          label = options.capabilities.getCapability("device").toString,
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