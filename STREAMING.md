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

## Render targets and live resize

A session's resolution is a per-stream choice, independent of the virtual
display it runs on. `StreamConfig.width`/`height` set the **render target**: the
page lays out at exactly that size and exactly that rectangle of the display is
captured, so any aspect ratio is rendered and captured verbatim.

```scala
val session = browser.stream.start(StreamConfig(width = Some(390), height = Some(844))).sync()
session.renderSize          // RenderSize(390, 844)
session.resize(1280, 820).sync()
```

`maxWidth`/`maxHeight` are a separate, unchanged knob — an encode-time downscale
of whatever was rendered, aspect preserved, never upscaling. Both apply:
`width = 1280, height = 800, maxWidth = 640` lays the page out at 1280x800 and
transmits 640x400.

### How a target is realised

1. **Layout** — CDP `Emulation.setDeviceMetricsOverride` at the target size. The
   emulated viewport paints at exactly that many physical pixels, anchored to the
   display's top-left, and the page sees the target as its viewport, so responsive
   CSS resolves to the mobile breakpoints a `390x844` target implies. A target
   equal to the display clears the override instead, leaving the kiosk window's
   native render path untouched.
2. **Capture** — `ximagesrc` always captures the whole display and a named
   `videocrop` (`capture-crop`) carves that same rectangle out of it, anchored
   top-left. The cropped frame *is* the target, so nothing is padded.
3. **Encode** — a named capsfilter (`encode-caps`) pins what the encoder
   receives. What that is, and whether it moves when the target does, is the
   encoder branch's `ResizeBehavior` — see below.

`StreamSession.resize` runs all three again **in place**. On the session's
GStreamer dispatcher it sets the crop insets from the display and the new target,
applies the branch's encode policy, and pushes an upstream force-key-unit event
into the encoder so the viewer repaints the new layout immediately rather than at
the next GOP boundary. Every property involved is settable while the pipeline
plays.

Nothing renegotiates. `webrtcbin`, its DTLS session, its ICE credentials and the
input DataChannel are untouched, so the session emits exactly one offer for its
whole lifetime and the viewer keeps decoding the track it already has — no black
frame, no re-handshake. This is possible because H.264 carries resolution in-band
(SPS/PPS on every IDR, re-injected by `h264parse config-interval=-1`) and the RTP
caps are resolution-independent: the SDP has nothing new to say about a size
change.

### Two resize behaviours, one per encoder branch

`PipelineBuilder.resizeBehavior` splits the encoders in two, because re-pinning a
playing encoder is only safe on one of them.

| Branch | `ResizeBehavior` | What a resize changes |
|---|---|---|
| `x264enc` | `Reconfigure` | crop **and** `encode-caps`; the transmitted frame becomes the new target |
| `vah264enc`, `vaapih264enc`, `nvh264enc` | `FixedCanvas` | crop and border only; `encode-caps` never changes |

**Why.** A hardware encoder allocates its surface pool per resolution, so
re-pinning a playing one is a driver decision rather than a GStreamer one. Some
drivers honour it. Others accept the new caps *silently* — properties set, no bus
error, stats and pipeline accounting following the request — and keep emitting the
resolution the pipeline was built with for the rest of the session. There is no
diagnostic to catch it on the server side; the only observer that can see it is
the viewer, whose `video.videoWidth` never moves. So the hardware branches simply
never ask: `encode-caps` is pinned once, when the pipeline is built, and every
render target is scaled into that canvas instead.

**The canvas** is `PipelineBuilder.encodeCanvas`: the session's display, bounded
per dimension by `maxWidth` / `maxHeight`. Every render target a session can reach
is a rectangle of its display, so a canvas that size is never a downscale of any
of them. That does mean a fixed-canvas branch encodes the canvas whatever the
target is — a 390x844 preview on a 1920x1080 display is a 1920x1080 encode. The
bitrate cap is unchanged (CBR), and the border compresses to nearly nothing, so
the cost is encode pixels rather than bandwidth. A consumer that wants a cheaper
canvas allocates a smaller virtual display or sets `maxWidth` / `maxHeight`; both
already existed and both now bound encode cost directly.

**The border** is a `videobox` (`capture-border`) sitting after the crop on those
branches, with negative insets that square the cropped target up to the canvas's
aspect ratio, centred. It fills with real black. Leaving the borders to the
scaler's `add-borders` instead is *not* equivalent: the VA scaler borders with
whatever the driver initialises its surface to, which on Mesa is a zeroed NV12
buffer — dark green on screen. `vapostproc add-borders=true` and the
`pixel-aspect-ratio=1/1` pin on the encode caps stay as the safety net for the
rounding residue, so a reshaped target can never reach the encoder stretched.

### Which size is which

Three sizes are now distinct, and they are reported separately rather than
conflated:

- **`StreamSession.renderSize` / `StreamStats.renderSize`** — the render target.
  The size the page laid out at and the rectangle of the display captured. This
  is authoritative for "what was asked for", on both branches, and it is what a
  consumer that requested a pane size should read back.
- **`StreamSession.encodedSize` / `StreamStats.width`/`height`** — the
  transmitted frame, which is exactly what a viewer's `video.videoWidth` /
  `videoHeight` report. On a `Reconfigure` branch this equals the render target
  (after `maxWidth`/`maxHeight`); on a `FixedCanvas` branch it is the canvas, and
  it does not change for the session's lifetime.
- **`StreamSession.placement` / `StreamStats.placement`** — a `RenderPlacement`
  saying where the render target sits inside the transmitted frame: the content
  sub-rectangle and its offset, with `bordered` telling you whether there is any
  border at all. This is the crop a consumer applies to present the content
  region alone.

The session also pushes the placement to the viewer, as a `placement` field on
the capture tap's existing throttled frame stamp — carried on the first stamp
after it changes, and absent otherwise. On a fixed-canvas branch that field is
the *only* signal the page behind the video changed shape, since the frame size
deliberately does not.

It rides the stamp rather than a message of its own deliberately. How many
messages a session originates, and when, is load-bearing on this stack: an extra
server-to-client write — at channel open, or alongside a stamp — is enough to
disturb the SCTP association and lose viewer input. The write pattern is
therefore exactly what it was, one throttled stamp, with a field added.

Input coordinates keep arriving in transmitted-frame pixels; `InputRouter` maps
them back through the same placement, so a click inside the content region lands
on the page it looks like it landed on whether or not there is border around it.

### Why the display is a bound, not a knob

The virtual display's size is fixed for its lifetime. Xvfb builds the RANDR
extension and reports geometry through it, but advertises exactly one mode — the
framebuffer it was spawned with — and rejects `RRSetScreenSize` outright:

```
$ xrandr --display :100 --fb 390x844
xrandr: specified screen 390x844 not large enough for output screen (1920x1080+0+0)
X Error of failed request:  BadValue ... Minor opcode of failed request: 21 (RRSetCrtcConfig)
```

`xrandr --newmode` reports success and registers nothing, because the server has
no `RRCreateMode`. Restarting Xvfb on the same display number is not a fallback
either: it severs the X connection of every client on that display, and the
browser rendering there exits with `X connection error received`.

So `VirtualDisplay.resize` attempts the RandR path (select an advertised mode,
register and select one, set the framebuffer) and verifies the result against the
server's reported dimensions. When the server refuses it raises
`DisplayResizeUnsupportedException`, which names both sizes and points at
`VirtualDisplayConfig`. Rendering *smaller* than the display never reaches that
path — that is a capture region, which needs nothing from the server. Allocate the
display at the largest size a session will ever need.

`Browser.setWindowBounds` is deliberately not part of this. Resizing the kiosk
window means leaving fullscreen, which brings Chrome's tab strip and toolbar back
into the captured pixels, and Chrome clamps a top-level window to a 500px minimum
width — a `390`-wide target is unreachable that way. Device-metrics emulation has
neither problem.

## Native object ownership

Every GStreamer handle a session touches has exactly one owner, and the session
releases what it owns at a known point instead of leaving it to gst1-java-core's
reference reaper — a handle freed twice corrupts the process heap, and the abort
lands at whatever allocation comes next rather than at the mistake.

The binding hands back borrowed pointers as owning handles in two places on this
path:

- **Nested structures.** `Structure.getValue` returns a boxed `GstStructure` — the
  per-transport blocks inside webrtcbin's `get-stats` reply — as a handle owning a
  pointer that belongs to the parent. Those blocks only exist once media is
  flowing, so a session that never had a viewer never meets this. `stats` releases
  each block and the reply as soon as it has read them, and disposes the promise
  the reply hangs off within the same call.
- **Session descriptions.** The offer passed to `create-offer`'s callback belongs
  to the promise that carried it, while `getSDPMessage` returns a copy that does
  not. The offer is let go of unfreed; the copy is disposed.

The opposite mistake leaks: `WebRTCBin.setRemoteDescription` disowns the
description it is handed, so the answer is applied by emitting
`set-remote-description` directly and freeing the description — which owns the
parsed SDP message — once webrtcbin has taken its copy.

Elements fetched by name, static pads, caps built for a property and the
force-key-unit event are all references this side owns, each released where it is
used. Teardown disconnects the bus and webrtcbin signal handlers first, closes the
DataChannel, brings the pipeline to NULL, and only then releases the session's
references on elements inside it. `GST_TRACERS=leaks` reports nothing alive after
a session stops.

## Clean capture profiles

Headful capture records everything Chrome draws, including its own UI. The reliable suppression levers on `BrowserConfig`:

- `passwordManager = false` — writes `credentials_enable_service = false` and `profile.password_manager_enabled = false` into the profile Preferences, killing the "Save password?" bubble without `--enable-automation` (which adds the automation infobar). `--incognito` does NOT suppress the bubble.
- `testType = true` — `--test-type` suppresses the automation infobar and assorted first-run/security prompts.
- `disableFeatures = List("Translate", ...)` and `extraArgs` (e.g. `--hide-crash-restore-bubble`, `--no-first-run`) for the remaining bubbles.
- `disableInfobars` is a no-op on Chrome 76+ — don't rely on it.
