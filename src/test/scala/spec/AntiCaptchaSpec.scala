package spec

import com.outr.robobrowser.remote.RoboRemote
import com.outr.robobrowser.{AntiCaptcha, DriverLoader, RoboBrowser}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.youi.net._

import java.io.File
import scala.io.Source

class AntiCaptchaSpec extends AnyWordSpec with Matchers {
  "AntiCaptcha" should {
    val key: String = {
      val file = new File("anticaptcha.key")
      assert(file.isFile, "You must create a file 'anticaptcha.key' in the working directory with your api key")
      val s = Source.fromFile(file)
      try {
        s.mkString.trim
      } finally {
        s.close()
      }
    }
    lazy val browser = new RoboRemote() with AntiCaptcha {
      override protected def antiCaptchaApiKey: String = key
    }
    "load reCAPTCHA 2 to verify" in {
      browser.load(url"https://antcpt.com/eng/information/demo-form/recaptcha-2.html")
      browser.oneBy("[name='demo_text']").sendInput("Test input")
      browser.oneBy("input[type=submit]").click()
      browser.oneBy("h2").text.trim should be("Thank you, your message \"Test input\" was posted!")
    }
    "dispose the browser" in {
      browser.dispose()
    }
  }
}
