package robobrowser.stream

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PipelineBuilderSpec extends AnyWordSpec with Matchers {
  private val fullHd = RenderSize(1920, 1080)

  "PipelineBuilder.targetSize" should {
    "be the whole display when no target is configured" in {
      PipelineBuilder.targetSize(fullHd, StreamConfig()).should(be(fullHd))
    }
    "honour a portrait target verbatim" in {
      PipelineBuilder.targetSize(fullHd, StreamConfig(width = Some(390), height = Some(844)))
        .should(be(RenderSize(390, 844)))
    }
    "inherit the display's other dimension from a partial request" in {
      PipelineBuilder.targetSize(fullHd, StreamConfig(width = Some(800))).should(be(RenderSize(800, 1080)))
      PipelineBuilder.targetSize(fullHd, StreamConfig(height = Some(600))).should(be(RenderSize(1920, 600)))
    }
    "round an odd target down to even dimensions" in {
      PipelineBuilder.targetSize(fullHd, StreamConfig(width = Some(391), height = Some(845)))
        .should(be(RenderSize(390, 844)))
    }
  }

  "PipelineBuilder.encodeSize" should {
    "pass the target through with no bounds" in {
      PipelineBuilder.encodeSize(fullHd, StreamConfig()).should(be(fullHd))
    }
    "downscale to fit maxWidth preserving aspect" in {
      PipelineBuilder.encodeSize(fullHd, StreamConfig(maxWidth = Some(1280))).should(be(RenderSize(1280, 720)))
    }
    "downscale to fit the tighter of both bounds" in {
      PipelineBuilder.encodeSize(fullHd, StreamConfig(maxWidth = Some(1600), maxHeight = Some(720)))
        .should(be(RenderSize(1280, 720)))
    }
    "never upscale" in {
      PipelineBuilder.encodeSize(RenderSize(1280, 720), StreamConfig(maxWidth = Some(1920)))
        .should(be(RenderSize(1280, 720)))
    }
    "round odd results down to even dimensions" in {
      PipelineBuilder.encodeSize(RenderSize(1366, 768), StreamConfig(maxWidth = Some(683)))
        .should(be(RenderSize(682, 384)))
    }
    "cap a portrait target without coercing its aspect" in {
      val config = StreamConfig(width = Some(390), height = Some(844), maxHeight = Some(422))
      val target = PipelineBuilder.targetSize(fullHd, config)
      PipelineBuilder.encodeSize(target, config).should(be(RenderSize(194, 422)))
    }
    "leave a portrait target alone when the caps are looser than it is" in {
      val config = StreamConfig(width = Some(390), height = Some(844), maxWidth = Some(1920), maxHeight = Some(1080))
      PipelineBuilder.encodeSize(PipelineBuilder.targetSize(fullHd, config), config).should(be(RenderSize(390, 844)))
    }
  }

  "PipelineBuilder.encodeCaps" should {
    "be exactly what the launch description pins, so a resize swaps in the same shape" in {
      val config = StreamConfig(width = Some(1280), height = Some(820))
      List("vah264enc", "vaapih264enc", "nvh264enc", "x264enc").foreach { encoder =>
        PipelineBuilder.description(":100", fullHd, config, encoder)
          .should(include(s"caps=${PipelineBuilder.encodeCaps(encoder, RenderSize(1280, 820))}"))
      }
    }
  }

  "PipelineBuilder.description" should {
    val base = PipelineBuilder.description(":100", fullHd, StreamConfig(), "x264enc")

    "capture the requested display with damage tracking and pointer" in {
      base.should(include("ximagesrc display-name=:100 use-damage=true show-pointer=true"))
    }
    "omit the pointer when configured" in {
      PipelineBuilder.description(":100", fullHd, StreamConfig(showPointer = false), "x264enc")
        .should(include("show-pointer=false"))
    }
    "configure webrtcbin with stun and low-latency jitterbuffer" in {
      base.should(include("webrtcbin name=webrtc bundle-policy=max-bundle latency=40 stun-server=stun://stun.l.google.com:19302"))
    }
    "include a turn server when configured" in {
      PipelineBuilder.description(":100", fullHd,
        StreamConfig(turnServers = List("turn://u:p@host:3478")), "x264enc")
        .should(include("turn-server=turn://u:p@host:3478"))
    }
    "cap the frame rate" in {
      PipelineBuilder.description(":100", fullHd, StreamConfig(maxFps = 30), "x264enc")
        .should(include("video/x-raw,framerate=30/1"))
    }
    "map bitrate to kbit/s with zerolatency x264 settings" in {
      base.should(include("x264enc name=video-encoder tune=zerolatency speed-preset=ultrafast " +
        "bitrate=8000 key-int-max=120 bframes=0"))
      base.should(include("video/x-h264,profile=constrained-baseline"))
    }
    "always capture the whole display, never a region" in {
      base.shouldNot(include("startx="))
      PipelineBuilder.description(":100", fullHd, StreamConfig(width = Some(390), height = Some(844)), "x264enc")
        .shouldNot(include("startx="))
    }
    "pin the encode caps even at native size, so a resize has caps to swap" in {
      base.should(include("capsfilter name=encode-caps caps=video/x-raw,format=I420,width=1920,height=1080"))
    }
    "leave the crop open at native size" in {
      base.should(include("videocrop name=capture-crop left=0 top=0 right=0 bottom=0"))
    }
    "narrow the encode caps when downscaling" in {
      PipelineBuilder.description(":100", fullHd, StreamConfig(maxWidth = Some(1280)), "x264enc")
        .should(include("capsfilter name=encode-caps caps=video/x-raw,format=I420,width=1280,height=720"))
    }
    "crop the capture to a portrait target and encode it at that size" in {
      val portrait = PipelineBuilder.description(":100", fullHd,
        StreamConfig(width = Some(390), height = Some(844)), "x264enc")
      portrait.should(include("videocrop name=capture-crop left=0 top=0 right=1530 bottom=236"))
      // The encoded frame is the cropped rectangle, so a non-16:9 target is
      // never padded out to the display's aspect
      portrait.should(include("capsfilter name=encode-caps caps=video/x-raw,format=I420,width=390,height=844"))
    }
    "crop to the target and still honour the encoder caps" in {
      val portrait = PipelineBuilder.description(":100", fullHd,
        StreamConfig(width = Some(390), height = Some(844), maxHeight = Some(422)), "x264enc")
      portrait.should(include("videocrop name=capture-crop left=0 top=0 right=1530 bottom=236"))
      portrait.should(include("capsfilter name=encode-caps caps=video/x-raw,format=I420,width=194,height=422"))
    }
    "crop for a landscape target that is not the display's aspect" in {
      PipelineBuilder.description(":100", fullHd, StreamConfig(width = Some(1280), height = Some(820)), "x264enc")
        .should(include("videocrop name=capture-crop left=0 top=0 right=640 bottom=260"))
    }
    "use VAMemory caps and GPU scaling for vah264enc" in {
      val va = PipelineBuilder.description(":100", fullHd, StreamConfig(maxWidth = Some(1280)), "vah264enc")
      va.should(include("vapostproc"))
      va.should(include("capsfilter name=encode-caps caps=video/x-raw(memory:VAMemory),format=NV12,width=1280,height=720"))
      va.should(include("vah264enc name=video-encoder rate-control=cbr bitrate=8000"))
      va.should(include("video/x-h264,profile=constrained-baseline"))
      va.shouldNot(include("videoscale"))
    }
    "name the encoder on every branch so a resize can force a keyframe through it" in {
      List("vah264enc", "vaapih264enc", "nvh264enc", "x264enc").foreach { encoder =>
        PipelineBuilder.description(":100", fullHd, StreamConfig(), encoder)
          .should(include(s"$encoder name=video-encoder"))
      }
    }
    "pin a browser-compatible profile on every encoder" in {
      List("vah264enc", "nvh264enc", "x264enc").foreach { encoder =>
        PipelineBuilder.description(":100", fullHd, StreamConfig(), encoder)
          .should(include("video/x-h264,profile=constrained-baseline"))
      }
    }
    "use ultra-low-latency NVENC settings" in {
      PipelineBuilder.description(":100", fullHd, StreamConfig(), "nvh264enc")
        .should(include("nvh264enc name=video-encoder preset=p1 tune=ultra-low-latency zerolatency=true " +
          "rc-mode=cbr bitrate=8000"))
    }
    "end at the named webrtcbin with RTP payloading" in {
      base.should(include("rtph264pay pt=96 config-interval=-1 aggregate-mode=zero-latency mtu=1200"))
      base.should(endWith("application/x-rtp,media=video,encoding-name=H264,payload=96 ! webrtc."))
    }
  }
}
