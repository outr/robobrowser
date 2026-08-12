package robobrowser

import fabric.io.JsonParser
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Files

/**
 * The clean-capture levers: `passwordManager = false` suppresses the
 * save-password bubble via profile Preferences (no CLI flag, so no
 * automation infobar); `testType` sends `--test-type`; `extraArgs`
 * passes arbitrary switches. `--incognito` is NOT a substitute (Chrome
 * still offers to save passwords in incognito automation profiles).
 */
class CaptureProfileConfigSpec extends AnyWordSpec with Matchers {

  "BrowserConfig capture levers" should {

    "send --test-type when testType is on and never emit a password flag" in {
      val config = BrowserConfig(testType = true, passwordManager = false)
      config.options should contain("--test-type")
      config.options.filter(_.contains("password")) shouldBe empty
      config.options should not contain "--enable-automation"
    }

    "append extraArgs after the structured options" in {
      val config = BrowserConfig(extraArgs = List("--hide-crash-restore-bubble", "--no-first-run"))
      config.options should contain("--hide-crash-restore-bubble")
      config.options.last shouldBe "--no-first-run"
    }

    "write password-manager-disabled preferences into the profile" in {
      val dir = Files.createTempDirectory("rb-capture-prefs").toFile
      val config = BrowserConfig(userDataDir = dir, passwordManager = false, disablePDFExtension = false)
      config.prepareUserDataDir()
      val prefs = JsonParser(dir.toPath.resolve("Default").resolve("Preferences"))
      prefs("credentials_enable_service").asBoolean shouldBe false
      prefs("profile")("password_manager_enabled").asBoolean shouldBe false
    }

    "merge password prefs alongside the PDF prefs without clobbering" in {
      val dir = Files.createTempDirectory("rb-capture-prefs-both").toFile
      val config = BrowserConfig(userDataDir = dir, passwordManager = false)
      config.prepareUserDataDir()
      val prefs = JsonParser(dir.toPath.resolve("Default").resolve("Preferences"))
      prefs("credentials_enable_service").asBoolean shouldBe false
      prefs("plugins")("always_open_pdf_externally").asBoolean shouldBe true
    }

    "leave preferences untouched when both levers are default" in {
      val dir = Files.createTempDirectory("rb-capture-prefs-off").toFile
      val config = BrowserConfig(userDataDir = dir, disablePDFExtension = false)
      config.prepareUserDataDir()
      Files.exists(dir.toPath.resolve("Default").resolve("Preferences")) shouldBe false
    }
  }
}
