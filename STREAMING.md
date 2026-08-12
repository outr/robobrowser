# Live Streaming Upgrade: CDP Screencast → WebRTC

> **Status: implemented** (M1–M6) in the `stream` module (`robobrowser-stream`) +
> `robobrowser.display` in cdp. See README "Live streaming (WebRTC)" for usage.
> Measured on LAN-localhost: 3–13 ms glass-to-glass at 720p/1080p ~57 fps via
> VAAPI. Deviations from this spec discovered during implementation:
> encoder-bitrate adaptation is *not* automatic with webrtcbin (fixed bitrate in
> v1; `rtpgccbwe` wiring is the follow-up), gst1-java-core lacks a DataChannel
> binding (a custom `WebRTCDataChannel` wrapper fills the gap), and encoders
> must pin `profile=constrained-baseline` or browsers reject the video m-line.

This document specifies how to upgrade RoboBrowser's live-view streaming from the
current CDP screencast to a WebRTC pipeline with hardware video encoding. It is
self-contained so the work can be tackled independently of any consumer of the
feature.

## Current state and why it's slow

`Screencast.scala` streams via CDP `Page.startScreencast`: Chrome emits each frame
as a **full JPEG**, software-encoded, **base64-encoded** (+33% size), delivered
through the DevTools **JSON websocket**, with input events round-tripping over the
same channel. That API was designed for DevTools' device-preview pane, not
interactive streaming:

- No inter-frame compression — every frame is a complete image.
- Chrome internally throttles screencast frame production.
- JSON/base64 framing adds parse and copy overhead on both ends.
- Practical ceiling ≈ 10–20 fps, visibly stuttery, regardless of tuning
  `quality` / `everyNthFrame`.

The ceiling is the transport, not our code. Keep `Screencast` as the
zero-dependency fallback; the upgrade adds a parallel fast path.

## Targets

| Metric | Target |
|---|---|
| Resolution / frame rate | 1080p at 30–60 fps sustained |
| Glass-to-glass latency (LAN/localhost) | ≤ 50 ms |
| Glass-to-glass latency (internet) | ≤ 150 ms |
| Encode CPU cost | Near zero (hardware encoder) with software fallback |
| Adaptation | Bitrate adapts to congestion automatically |

**Non-goal:** matching native local rendering. Streamed browsing always pays an
encode–transmit–decode tax (see Mighty's post-mortem); the goal is "smooth and
comfortable," not "indistinguishable from local."

## Architecture

```
capture (per-session X display) → encode (HW video) → WebRTC (media + input datachannel)
                                                          ↑
                          input events → CDP Input.dispatch* (existing code)
```

### 1. Capture

The README's Xvfb approach already has the browser painting headful to a virtual
display. Run **one browser per display** (`:100 + n`, allocated per session) so a
display capture is exactly one session's pixels:

- X11: GStreamer `ximagesrc display-name=:N use-damage=true` (damage events keep
  static pages nearly free, mirroring the change-driven behavior of CDP
  screencast).
- Wayland (future): PipeWire source; not needed while sessions run under Xvfb.
- Composite the cursor into the stream (`show-pointer=true`) — the client does
  not render a local cursor.

Note: Xvfb's framebuffer is CPU memory, so capture is a memcpy — acceptable at
1080p. If profiling shows otherwise, the alternative is Xdummy or headless EGL on
a real GPU surface.

### 2. Encode

GStreamer encoder selection, in preference order, probed at startup:

1. `vah264enc` / `vaapih264enc` — Intel/AMD VAAPI
2. `nvh264enc` — NVIDIA NVENC (mind per-GPU session limits for multi-session)
3. `x264enc tune=zerolatency speed-preset=ultrafast` — software fallback

H.264 first for universal decode support; AV1 (`vaav1enc`/`nvav1enc`) is a later
option behind the same abstraction. Configure for low latency: no B-frames, short
GOP, keyframes on demand (respond to PLI from the client).

### 3. Transport

**`webrtcbin`** (GStreamer's WebRTC element) carries the media: it provides
ICE/DTLS/SRTP and congestion control (transport-cc/REMB) for free, which is what
makes bitrate adaptation automatic. Signaling (SDP/ICE exchange) rides the
existing spice websocket infrastructure.

JVM integration options, in order of preference:

1. `gst1-java-core` bindings driving an in-process pipeline.
2. A GStreamer child process per session, managed the same way `CDP.createProcess()`
   manages browsers, with signaling proxied over stdio/socket.
3. If webrtcbin proves painful from the JVM: a small sidecar (e.g. Pion/Go) that
   consumes RTP from GStreamer — last resort, adds a runtime dependency.

### 4. Input

A WebRTC **DataChannel** carries input from client to server (lower jitter than
the websocket, ordered + reliable mode). Server side maps events onto the CDP
input dispatch already in the codebase (`Mouse.scala`, `KeyFeatures`):

- Coordinate mapping: stream pixels → device pixels using the same
  metadata approach as `ScreencastFrameMetadata` (device size, page scale,
  scroll offset).
- Wheel events map to `Input.dispatchMouseEvent` type `mouseWheel` so pages get
  native scroll behavior.
- Serialize events as fabric JSON for consistency with the rest of the wire
  layer.

## API sketch

Follow existing conventions (Scala 3, `Task` from rapid, lazy feature attachment
like `Screencast`):

```scala
// Reached as browser.stream, alongside the retained browser.screencast fallback
class Stream(browser: RoboBrowser) {
  def start(config: StreamConfig = StreamConfig()): Task[StreamSession]
}

case class StreamConfig(codec: Codec = Codec.H264,       // H264 | AV1
                        maxBitrate: Int = 8_000_000,
                        maxFps: Int = 60,
                        maxWidth: Option[Int] = None,
                        maxHeight: Option[Int] = None)

trait StreamSession {
  def signaling: Channel[SignalMessage]   // reactify Channel: SDP/ICE both directions
  def stats: Task[StreamStats]            // fps, bitrate, RTT (from webrtcbin getStats)
  def stop(): Task[Unit]
}
```

Client side: replace the `<img>` frame-swap viewer with a minimal
`RTCPeerConnection` player (`<video>` element + one datachannel for input).

## Milestones

| # | Deliverable | Acceptance |
|---|---|---|
| M1 | Display allocator + capture/encode pipeline to a local preview | 1080p60 sustained from an Xvfb session; encoder probe picks HW when present |
| M2 | webrtcbin + signaling over spice; browser client plays the stream | Video visible in a web page on another machine |
| M3 | DataChannel input mapped to CDP dispatch | Type, click, scroll round-trip feels usable |
| M4 | Multi-session: N concurrent browsers/displays/streams | No cross-session frame or input bleed |
| M5 | Fallback + stats | Automatic drop to CDP screencast when GStreamer/WebRTC unavailable; `stats` reports fps/bitrate/RTT |
| M6 | Latency measurement harness | Timestamp-overlay page measured on-screen vs remote; targets table verified |

## Measurement

Glass-to-glass: navigate the session to a page rendering a high-resolution
timestamp, film/capture the client `<video>` alongside a local clock, diff.
Automated proxy: emit a timestamp via DataChannel on each keyframe request and
compare against `video.requestVideoFrameCallback` arrival. Report continuously
via `StreamSession.stats`.

## Risks

- **No hardware encoder present** — x264 zerolatency fallback works but costs
  CPU; surface which encoder was selected in `StreamStats`.
- **DRM (Widevine) content** may render black in captures; document as a known
  limitation rather than fighting it.
- **NVENC session caps** limit concurrent sessions per consumer GPU; the display
  allocator should track encoder sessions and overflow to software.
- **webrtcbin from the JVM** is the least-proven integration point — de-risk it
  first in M2; the child-process fallback is the escape hatch.

## Downstream consumer

This upgrade stands on its own (it transforms any RoboBrowser live-view use
case), but it is also the substrate for the Spaceship browser's planned 2.0
"Remote Spaces" feature — streamed, remotely-hosted browsing contexts. Keeping
the work independent here is deliberate: RoboBrowser ships it as a
general-purpose capability.

## Clean capture profiles

Headful capture records everything Chrome draws, including its own UI. The reliable suppression levers on `BrowserConfig`:

- `passwordManager = false` — writes `credentials_enable_service = false` and `profile.password_manager_enabled = false` into the profile Preferences, killing the "Save password?" bubble without `--enable-automation` (which adds the automation infobar). `--incognito` does NOT suppress the bubble.
- `testType = true` — `--test-type` suppresses the automation infobar and assorted first-run/security prompts.
- `disableFeatures = List("Translate", ...)` and `extraArgs` (e.g. `--hide-crash-restore-bubble`, `--no-first-run`) for the remaining bubbles.
- `disableInfobars` is a no-op on Chrome 76+ — don't rely on it.
