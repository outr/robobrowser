# Native-resource rendering demonstrator

> **Architecture change — 2026-11-05:** #2 (one local JavaScript executor) is the sole browser path in 2.5.0-SNAPSHOT. DOM projection, pixel fallback, and dual-copy execution are removed. Earlier projection and Voidcraft remote-CDP evidence below is historical. See [current architecture](mirror/README.md); frontend disconnect still stops agent execution.

This is the historical proof report. The current default implementation, streaming transport, native canvas/MediaSource support, tests and limits are documented in [NATIVE_RENDERING_BUILD.md](NATIVE_RENDERING_BUILD.md).

This replaces the rendering premise for a focused proof, not the full implementation. The earlier raster-based video approach did not satisfy the requested native browser experience. Passing its frame-update checks was not evidence of native playback or visual quality.

Start the managed demo with `scripts/mirror-demo start`. Keep port 8898. Open its authorized URL with `?renderer=native` before the `#` token, or click **Native demo** in the toolbar. In native mode, that button loads `/fixture-native`. The ordinary URL still selects the earlier renderer for comparison. Native mode does not automatically switch to screenshots.

## What the proof does

The source browser executes the page's scripts and retains its cookies. Captured media descriptors identify the source-selected resource. Host-side resource access tries the browser's loaded body first and otherwise loads it through the source browser with its credentials. A document-scoped capability delivers the bytes to the viewer, which creates a real media Blob URL. No source URL or cookie is needed for local playback.

The frontend runs a real video decoder, native controls and its own playback clock. It forwards play, pause, seek, playback-rate, mute and volume actions to the source element. Source playback transitions and significant seeks synchronize back. Repeated old snapshots do not overwrite a new local action. Website scripts remain absent from the viewer.

DOM updates retain node identity. Where supported, state-preserving DOM moves avoid restarting media during reordering. The native mode has one scroll container; an outer scrollbar no longer changes the mirrored viewport width. Source CSS and image resources still render locally.

The fixture includes a cookie-protected 12-second, 30 fps H.264 test clip, a protected image, native input, a source-executed form, a menu, nested scrolling, document navigation and four DOM updates per second. The clip is generated with:

```bash
ffmpeg -f lavfi -i 'testsrc2=size=640x360:rate=30' -t 12 \
  -c:v libx264 -preset fast -crf 28 -pix_fmt yuv420p \
  -movflags +faststart -an native-clip.mp4
```

## Acceptance and comparison

Run:

```bash
sbt 'mirror/Test/runMain spec.MirrorNativeRenderingAcceptance target/native-rendering/final'
```

The test measures presented video frames, decode count, layout changes, media resets, buffering events and local typing feedback. It clicks the native play control through CDP mouse input, verifies source effects, adds 150 ms to snapshot responses, and checks playback during DOM churn. It separately measures the same fixture directly in Chrome.

An isolated instance of the actual application proxy is measured against the same fixture. Its comparison harness is retained with the local evidence under `mirror/target/native-rendering/proxy/`. No normal application client service is changed. The proxy executes website scripts locally; this demonstrator keeps execution remote. Matching their playback and layout measurements is the aim, not a claim that the new mode outperforms the proxy.

Measured results and screenshots live in `mirror/target/native-rendering/`, indexed by `evidence.json`. Final five-second measurements:

| Path | Presented frames | Layout shifts | Buffering / media resets |
| --- | ---: | ---: | ---: |
| Native renderer, local host | 150 | 0 | 0 / 0 |
| Native renderer, 150 ms snapshot response delay | 150 | 0 | 0 / 0 |
| Native renderer, separate host JVM | 150 | 0 | 0 / 0 |
| Separate host with delayed snapshots | 151 | 0 | 0 / 0 |
| Direct Chrome baseline | 149–151 | 0 | 0 / 0 |
| Actual application proxy | 150 | 0 | 0 / 0 |

The local native typing sample covered 13 characters: 0.6–15.6 ms from `beforeinput` to the next animation frame. This is a small fixture sample, not a latency guarantee. Both local and separate-host native runs passed 17 checks, including native play-button input, cookie isolation, source seek/rate changes, exactly-once form submission, menu actions, nested scrolling and navigation. All 36 unit tests and the existing media regression passed. The live port-8898 smoke measured 150 frames, no layout shifts, no raster frames and no viewer errors; desktop and narrow screenshots were inspected.

The evidence index preserves development failures. Native-control and live-navigation harness mistakes are identified separately from product fixes. The last live smoke waits for the new document generation rather than sampling the outgoing page.

## Boundaries before a production design

- Media is currently a bounded, complete-file transfer (16 MiB per resource, shared 64 MiB viewer asset cache). Blob URLs support native seeking after download. This is **not** a progressive/range-streaming transport for large files. CDP cache misses can cause an additional host-side fetch.
- MSE, live streams, DRM, unsupported codecs and cross-browser media compatibility are not established. Source and viewer playback clocks are approximately synchronized, not frame-locked. Audio forwarding is not part of the measured clip.
- Canvas/WebGL drawing-command transport is not implemented. Native mode marks this limitation and does not substitute compositor crops. GitHub's entire animated landing page therefore remains outside acceptance.
- Native local controls do not eliminate network latency for source JavaScript handlers. Broader framework/menu/input behavior still needs representative workflow tests.
- This proof is not deployed into the application's remote-browser mode. Production resource streaming, backpressure, lifecycle, multi-viewer playback ownership and broader compatibility need design and acceptance before integration.

The evidence supports proceeding with resource-backed native rendering. It does not establish a complete native sub-browser or arbitrary-site parity.

## Native control stability follow-up

A longer trace exposed redundant assignments to native media properties on every DOM update, including repeated removal/restoration of `tabindex`. Ten seconds produced 295 redundant attribute mutations; direct Chrome produced none. Native mode now preserves the browser's intrinsic media focus behavior and writes `controls`, `loop`, `playsinline` and `preload` only when their values change.

The updated 35-second trace recorded zero media-attribute mutations and zero programmatic playback-position corrections. Frame cadence was comparable to direct Chrome: about 30 fps, with roughly 50 ms p95 callback intervals in both paths. Natural loop-boundary seeking events occurred in both paths. These measurements do not establish that every perceived stutter is gone. Detailed traces and control regression results are indexed in `mirror/target/native-playback-timing/evidence.json`.
