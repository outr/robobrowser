//package spec
//
//import com.outr.robobrowser.browser.chrome.Chrome
//import com.outr.robobrowser.AntiCaptcha._
//import com.outr.robobrowser.{By, RoboBrowser}
//import org.scalatest.matchers.should.Matchers
//import org.scalatest.wordspec.AnyWordSpec
//import profig.Profig
//import spice.net.interpolation
//
//import java.io.File
//import scala.io.Source
//
//class AntiCaptchaSpec extends AnyWordSpec with Matchers {
//  "AntiCaptcha" should {
//    val apiKey: String = {
//      Profig.initConfiguration()
//      Profig("antiCaptchaKey").as[String]
//    }
//    lazy val browser = Chrome.withAntiCaptcha(apiKey).create()
//    "load reCAPTCHA 2 to verify" in {
//      browser.load(url"https://antcpt.com/eng/information/demo-form/recaptcha-2.html")
//      browser.oneBy(By.css("[name='demo_text']")).sendKeys("Test input")
//      browser.oneBy(By.css("input[type=submit]")).click()
//      browser.oneBy(By.css("h2")).text.trim should be("Thank you, your message \"Test input\" was posted!")
//    }
//    "dispose the browser" in {
//      browser.dispose()
//    }
//  }
//}
