package robobrowser.display

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}

class DisplayAllocatorSpec extends AnyWordSpec with Matchers {
  private def withFakeTmp[Return](f: Path => Return): Return = {
    val tmp = Files.createTempDirectory("robobrowser-display-spec")
    Files.createDirectories(tmp.resolve(".X11-unix"))
    try {
      f(tmp)
    } finally {
      Files.walk(tmp).sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.delete(p))
    }
  }

  "DisplayAllocator" should {
    "consider an unclaimed display free" in {
      withFakeTmp { tmp =>
        DisplayAllocator.isDisplayTaken(100, tmp).should(be(false))
      }
    }
    "consider a display with a lock file taken" in {
      withFakeTmp { tmp =>
        Files.createFile(tmp.resolve(".X100-lock"))
        DisplayAllocator.isDisplayTaken(100, tmp).should(be(true))
      }
    }
    "consider a display with a socket taken" in {
      withFakeTmp { tmp =>
        Files.createFile(tmp.resolve(".X11-unix").resolve("X100"))
        DisplayAllocator.isDisplayTaken(100, tmp).should(be(true))
      }
    }
    "skip locked and excluded displays when finding the next free number" in {
      withFakeTmp { tmp =>
        Files.createFile(tmp.resolve(".X100-lock"))
        Files.createFile(tmp.resolve(".X11-unix").resolve("X101"))
        DisplayAllocator.nextFree(100, excluded = Set(102), tmp).should(be(103))
      }
    }
    "start scanning at the requested base display" in {
      withFakeTmp { tmp =>
        DisplayAllocator.nextFree(250, excluded = Set.empty, tmp).should(be(250))
      }
    }
  }
}
