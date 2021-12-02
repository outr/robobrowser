package spec

import com.outr.robobrowser.{By, RoboBrowser}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.youi.net._

import java.io.File
import scala.io.Source

class AntiCaptchaSpec extends AnyWordSpec with Matchers {
  "AntiCaptcha" should {
    val apiKey: String = {
      val file = new File("anticaptcha.key")
      assert(file.isFile, "You must create a file 'anticaptcha.key' in the working directory with your api key")
      val s = Source.fromFile(file)
      try {
        s.mkString.trim
      } finally {
        s.close()
      }
    }
    lazy val browser = RoboBrowser.Remote.antiCaptcha(apiKey).create()
    "load reCAPTCHA 2 to verify" in {
      browser.load(url"https://antcpt.com/eng/information/demo-form/recaptcha-2.html")
      browser.oneBy(By.css("[name='demo_text']")).sendKeys("Test input")
      browser.oneBy(By.css("input[type=submit]")).click()
      browser.oneBy(By.css("h2")).text.trim should be("Thank you, your message \"Test input\" was posted!")
    }
    "dispose the browser" in {
      browser.dispose()
    }
  }
}
