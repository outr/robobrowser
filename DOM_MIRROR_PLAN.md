# RoboBrowser DOM mirroring implementation plan

> **Architecture change — 2026-11-05:** #2 (one local JavaScript executor) is the sole browser path in 2.5.0-SNAPSHOT. DOM projection, pixel fallback, and dual-copy execution are removed. Earlier projection and Voidcraft remote-CDP evidence below is historical. See [current architecture](mirror/README.md); frontend disconnect still stops agent execution.

Status: **native resources, dedicated workers, tab activation and media-clock fixes passed final packaged and two-hour endurance acceptance (2026-11-03)**. Current implementation, measured evidence and explicit limits are tracked in [NATIVE_RENDERING_BUILD.md](NATIVE_RENDERING_BUILD.md). Historical checks and completion claims below establish only their stated behavior, not arbitrary-site equivalence. The subsequent address-bar focus and pointer-motion fixes and their targeted validation are recorded at the top of that report.

The current managed demo uses port **8898**. Run `scripts/mirror-demo start` to obtain its stable authorized URL, and `scripts/mirror-demo restart` after changes. Refresh that URL after restarting; remote tabs reset. Older port numbers below identify historical test instances, which have been stopped.

## Performance and reliability follow-up — 2026-11-02

Four additional changes address measured work or reproduced failures:

- Native CSS animations no longer force full DOM traversal on every poll. The matched 600-row animation fixture went from 134 scans to six over roughly three seconds (about 96% fewer scans). Visible animation, DOM mutation and CSSOM updates still pass. CPU timings varied between runs and are not a stable speedup claim.
- Tab listing uses titles already returned by browser target discovery, eliminating one page evaluation per tab. A hostile `document.title` getter is never invoked by listing.
- Long projection errors cannot wrap the demo header and repeatedly change viewport height. This fixes the reproduced stylesheet-budget error/resize feedback loop; oversized-style rejection, recovery and delayed/rejected resize checks pass.
- Explicit cross-document navigation first stops the superseded load. A bounded wait handles an explicitly rejected stop during an inactive-page transition. Fragment navigation preserves in-flight resources. Timed-out navigation is never replayed; CDP timeout messages identify method, send/response phase and unknown outcome.

The navigation failure was reproduced while reloading GitHub with decoded video and navigating to Wikipedia: `Page.navigate` timed out after 20 seconds. The first cancellation-only fix also reproduced an inactive-page rejection. The final version passed 20 media-active reload/navigation cycles and a separate 18-visit, six-site journey, with no missing expected source-loaded images, failed asset checks or viewer errors. These runs exercise the observed race; they do not prove the absence of every navigation race. Chromium's [page handler](https://chromium.googlesource.com/chromium/src/+/refs/heads/main/content/browser/devtools/protocol/page_handler.cc) explicitly requires an active frame for `StopLoading`.

Focused regressions include profile persistence, fragment navigation with a delayed image, tab metadata, animation capture, stylesheet limits/recovery, viewport readiness, address entry, popups, latency, media, nested/cross-origin frame rasters and file transfers, plus 36 unit tests. Final native acceptance passed 33 application checks (including all 26 browser tools), five backend-restart checks and ten Hero–Meg checks. Packaged viewer/capture/demo resources match current source, and the native binary declares baseline x86-64. The port-8898 GitHub smoke recorded advancing canvas/video frames with no viewer error; its screenshot was inspected. Meg probe processes were confirmed stopped. Results, source hashes and logs are recorded in the evidence index. Normal client services are not updated by isolated acceptance. Authenticated public workflows, audio/device/DRM forwarding, native-rate video, non-Chromium platforms and a fresh two-hour endurance run remain outside this follow-up's established acceptance.

## Audit remediation — 2026-11-02

The six findings below have corresponding changes: bounded graceful host shutdown; non-editable keydown/up forwarding; stale-image removal; trusted source-bound video controls; application-owned workspace browser/profile allocation; and rebuilt local/native dependencies. RoboBrowser production code remains application-neutral. An additional initial-layout/viewport race discovered during repeated pointer testing now has a delayed-resize regression covering DOM and pixel modes. A subsequent native Meg check also exposed premature readiness after a rejected resize; the viewer now retries while withholding input readiness. Mode changes reissue outstanding resizes so an obsolete callback cannot leave input suspended.

Fresh checks cover immediate persistent-profile reopen, image removal/restoration, canvas key code/modifiers/release, functional video playback/seek/rate, repeated pointer input, native editing, latency, and live same/cross/nested-frame media. The managed demo remains on port 8898. Native application, workspace, restart and two-machine checks are recorded separately from ordinary unit/browser tests. Earlier two-hour endurance evidence is historical; current short soaks are not a replacement claim for it.

Final evidence: 36 unit tests; six client tests; functional browser regressions including rejected/delayed resize and mode changes during resize; source-bound video controls; 33 native application checks including all 26 registered tools; backend JVM restart; and ten real Hero–Meg checks. The final native binary contains the exact current renderer and declares baseline x86-64. The Meg click that exposed rejected-resize readiness now passes, as do reconnect and second-machine workspace isolation. Probe processes were confirmed stopped on Meg; normal client services were not updated.

Public navigation passed two subsequent 18-visit runs and a fresh six-site run after the final mode-switch correction. One earlier third-visit Wikipedia timeout remains in the evidence: its precise cause was not captured, so later passes do not establish that it was fixed. Host/control and mounted-viewer five-minute soaks passed; these do not certify arbitrary-site behavior or replace a fresh two-hour run. The final port-8898 rendered smoke has advancing GitHub canvas/video frames and no viewer error. These are bounded acceptance results, not a claim of 100% browser compatibility.

## Original failed functionality audit — 2026-11-02

Original verdict: **not ready for complete user acceptance**. Original audit evidence is preserved in `mirror/target/full-audit/baseline/report.json`; deterministic failure probes are checked in as `MirrorAuditAcceptance` and `MirrorProfileAudit`. Production implementation and normal Voidcraft clients were not changed during this audit.

| Priority | Reproduced failure or verified integration gap | Evidence / corrective gate |
| --- | --- | --- |
| High | Normal host stop can lose newly written persistent cookies and local storage. | `profile-isolation.json` fails; sending `Browser.close` and allowing exit preserves both in `profile-graceful.json`. Implement bounded graceful shutdown and verify restart persistence through the public host API. |
| High | Keyboard input on a focused canvas never reaches the source. | `gap-probes.json`: actual KeyK input produces no source event. Cover keydown/up, modifiers and application shortcuts without duplicating native text edits. |
| Medium | Removing an image's source leaves its old pixels in the viewer. | Source naturalWidth becomes zero; viewer retains its blob URL and width 120. Clear stale asset state and cover removal/replacement/retry races. |
| High | Native source video controls disappear in DOM mode. | Source controls=true, viewer controls=false. Provide source-bound playback/seek behavior; local controls on a poster alone are insufficient. |
| High | Voidcraft still allocates one account-profile host instead of independent workspace hosts. | Current `ClientBrowserHost` implementation has one host/backend pair and uses only the account hash for its profile. Allocation must remain in Voidcraft. |
| High | Voidcraft's configured local RoboBrowser artifact predates the live-region changes. | The configured 2.4.9-SNAPSHOT JAR has no raster loop. Updated application packaging and isolated native/two-machine acceptance remain outstanding. |

Fresh verification passed 36 unit tests, 21 existing browser acceptance invocations, 18 visits across six public sites, a 180-second GitHub live-region run, and live canvas/video pixel checks through same-origin, cross-origin and nested frames over the separate-JVM transport. Independent profiles isolate state while running; normal-stop persistence fails. The controlled benchmark measured 33 ms p95 source-to-viewer observation and 15 ms p95 local typing echo; these are fixture measurements, not arbitrary-site latency guarantees.

Passing tests do not cancel the failures above. Authenticated public-site journeys, audio/devices/DRM, native-rate media and non-Chromium platform acceptance remain unestablished. The prior two-hour soak and native application tests predate the present implementation. An interrupted extended audit soak is not counted as a pass; fresh 180-second host, mounted-viewer and control-scheduler runs all passed and are recorded separately in the audit report.

## Live-region correction — 2026-11-02

The earlier completion claim was too broad: GitHub's canvas was replaced by a placeholder and its HEVC video failed in the source headless browser. Native mode now includes bounded live canvas/video regions, coordinate input, generation-scoped disposal and independent canvas/video polling. Source canvas capture has a compositor fallback; the viewer remains scriptless. Optional sandboxed HEVC recovery uses the source-approved media response without changing its URL or bypassing CSP.

Current regression evidence includes 36 passing unit tests; real-browser canvas/WebGL/video pixel and input checks; cookie-protected, restrictive-CSP HEVC recovery and seeking; cross-frame, mode, scroll and latency checks; and three rounds across six public sites with no missing expected images or viewer errors. See `mirror/target/{raster-acceptance,media-acceptance,public-raster-acceptance,hybrid-navigation}`. The final live-region lifecycle run sampled GitHub for 90 seconds with repeated DOM/pixel switches, and the managed port-8898 demo passed a fresh rendered-browser smoke. Source hashes and test provenance are recorded in `mirror/target/hybrid-rendering-evidence.json`. These results cover the tested workflows, not universal browser compatibility. Native-rate media, audio forwarding, DRM and every transformed/composited edge case remain outside established acceptance. The earlier two-hour endurance and application integration runs predate this live-region implementation.

## Application allocation boundary — clarified 2026-11-02

RoboBrowser exposes generic independent browser/profile instances. It must not contain Voidcraft-specific code, workspace lookup, account routing or application protocols. Voidcraft's intended policy is one independent browser and persistent profile per user/workspace/machine, with tabs sharing only that workspace's profile. That allocation belongs entirely in Voidcraft. The application adapter now allocates scoped hosts and profiles, pins sessions to workspace generations, and filters automatic agent selection after workspace changes. This policy is implemented entirely in Voidcraft. RoboBrowser's existing `BrowserHost` already supports caller-selected profiles and exclusive profile ownership. The viewer's integration message is generic (`roboBrowser: 1`); compatibility with the former application-specific field is confined to Voidcraft's adapter.

## Implementation and evidence ledger

The [module guide](mirror/README.md) describes the implemented architecture, commands and boundaries. This ledger supersedes early checkpoints in the audited design below. The mirror depends on `core`, uses JDK HTTP/WebSocket transport, and integrates with Voidcraft's existing client tunnel.

| Milestone | Implementation and verification |
| --- | --- |
| 0. Feasibility | Scala host, packaged capture/viewer, isolated native documents, local editing, resource/font fixtures and standalone/routed demos; JVM and native-image launch/CDP probes. |
| 1. Host foundation | One browser per client with real tabs/contexts, flattened frame sessions, profile ownership, deadlines, shutdown, process failure and legacy API checks. |
| 2. Interactive demo | Human viewers and agents share the source tab; generation/revision patches, resync, script isolation, keyboard/pointer input, resources and explicit image fallback. Local and separate-JVM acceptance suites pass. |
| 3. Fidelity/recovery | Original stylesheet cascade, cross-origin imports, CSSOM/adopted sheets, media/container queries, root attributes, generated content, SVG, authenticated images/fonts, shadow roots and nested cross-origin frames. Recovery, stale-document, hostile-capture and bounded-queue checks pass. |
| 4. Native interaction | Local echo, selection, composition, rejected/delayed source normalization, basic rich text, scrolling, select/range controls, drag, popups, history, dialogs, files and viewport reset. Native workflow suites pass. |
| 5. Agent parity/endurance | All 26 actual WebTabTools schemas have effect/result and cancellation checks. The final UI viewer soak passes 2,315 rounds; the ten-minute control scheduler soak passes 114,590 handoffs and 229,180 duplicate requests without repeated effects. The two-hour host soak passes 27,784 rounds and 111,136 snapshots across four tabs, with 286,168 bytes of retained-heap growth. |
| 6. Voidcraft adapter | Machine/account pinning, per-viewer and agent grants, sharing/revocation, detached agents, popup control, files and recording. The rebuilt stylesheet-based Linux native client passes 27 application checks, all 26 tools, backend restart, six connection regressions and eight real Hero–Meg interruption/reconnect checks. |
| 7. Verification and demo | Native catalog/detail/history/pagination and article-section navigation pass. Public-page screenshots and performance measurements are recorded. The [final demo](http://127.0.0.1:8898/) passes desktop/narrow rendered smoke checks; host, viewer and control endurance checks pass. |

## Address-bar correction — 2026-11-02

User testing exposed a gap in the completed checks: snapshot polling could overwrite an edited address between pointer-down and Go, bare hosts were rejected, and subsequent snapshots cleared the navigation error. The viewer now preserves drafts and pending navigation, normalizes bare public hosts to HTTPS (loopback hosts to HTTP), and retains errors until another submission. `MirrorAddressBarAcceptance` reproduces the original Go race and now passes seven checks through actual browser input. Its evidence is `mirror/target/address-bar-acceptance/result.json`. This viewer-only correction postdates the endurance/native-client evidence above; those runs are not claimed as tests of this change. The updated demo uses port 8899.

## Large atomic stylesheet correction — 2026-11-02

Facebook's logged-out page exposed the original 20,000-rule capture ceiling (35,196 source CSS rules observed). Capture now allows at most 100,000 rules while retaining its 4 MiB stylesheet byte budget, accounts for cached sheets in aggregate budgets, and emits stylesheet chunks targeted at 256 KiB/2,048 resources rather than one viewer style element per rule. Chunk boundaries preserve complete top-level rules and cascade order. Known stylesheet-limit errors are reported explicitly. All 28 stylesheet acceptance checks pass, including a 35,000-rule regression, cascade restoration and cached-budget enforcement. The real Facebook native page probe passes with no missing images or source scripts in the viewer; logged-in workflows are untested. The new demo on port 8900 opens Facebook. These corrections postdate the original endurance and native-client evidence; current evidence is `mirror/target/facebook-verification.json`.

## Repeated stylesheet and GitHub correction — 2026-11-02

GitHub exposed repeated CSS bundles consuming the 4 MiB budget multiple times. Capture now shares identical, same-base stylesheet definitions and stable chunks, while retaining all ordered references (including repeated cascade occurrences). Rule-work and unique-byte budgets remain bounded; cached rules still count. Stylesheet reconstruction uses the existing 250 ms bootstrap budget, while ordinary incremental updates retain 100 ms and the renderer deadline remains one second. Oversized projection errors clear partial content and identify DOM unavailability instead of displaying Reconnecting.

The resource broker reuses source-browser loaded resource bodies, preserves image MIME types, and decodes data resources directly, avoiding inappropriate connect-src fetches for source-approved images. Three resource lifecycle tests pass, including restrictive CSP, credentialed HTTP semantics and cancellation. All 33 stylesheet checks and seven address-bar checks pass. GitHub's logged-out homepage passes the native probe and rendered inspection; canvas/video remains an explicit pixel-fallback boundary, and not every below-fold image was loaded at the probe timestamp. Current evidence is `mirror/target/github-verification.json`; earlier endurance/native-client results predate these changes.

## Capture completion and mode correction — 2026-11-02

A Facebook capture at 2146×936 could finish in 250–320 ms, then fail its final timing check and clear its caches. Completed, byte-checked results now survive soft-deadline overruns; traversal deadlines and the one-second renderer timeout remain. The 34-check stylesheet suite includes deterministic completed-overrun coverage. Six viewer-mode checks cover an in-flight DOM failure after switching to pixels and both mode choices across refresh. The real Facebook native probe passes, and the stable demo passes 19 consecutive captures after one initial loading error at the reported viewport. Evidence: `mirror/target/{mode-acceptance,facebook-wide-capture.json}`. These checks do not imply universal-site compatibility.

## Asset recovery after navigation — 2026-11-02

User testing exposed broken GitHub images after navigating from Facebook. The source browser had all 22 images, and all 22 resource capabilities were readable; the viewer failed to recover from transient reads and invalidated asset work on snapshot errors. Asset lifetimes now follow document identity, transient reads have bounded retries, failed unchanged images retry after bounded backoff, and stale callbacks/warnings cannot leak across documents. Both actual Facebook-to-GitHub navigation and deterministic failure/recovery tests pass; the real-page run loaded all 22 HTML images with no missing images. One GitHub stylesheet icon remains unavailable and is reported separately; canvas/video still use pixel fallback. These results do not establish complete third-party site compatibility. Evidence: `mirror/target/{asset-recovery-acceptance,cross-site-assets-acceptance}`.

## Repeated-navigation hardening — 2026-11-02

Repeated Facebook/GitHub/Wikipedia/catalog/documentation visits exposed two additional races. Scroll acknowledgement could overwrite a newer local position; the viewer now guards the latest gesture until its command completes and rejects snapshots captured across that edit. Separately, continuous capture work starved the periodic image/control reconciliation timer. A failing GitHub diagnostic showed two source-loaded images still marked `assetReady: false` in the viewer. Reconciliation now uses an independent clock. A deterministic animation/eventless-value regression failed before this change and passes afterward.

Image readiness prevents premature lazy-image transfers. Stylesheet resources transfer only after source demand, with a bounded CDP loaded-resource inventory supplementing Resource Timing for opaque cross-origin CSS. This supersedes the earlier unused GitHub icon warning; protected cross-origin font/image fixtures remain required. Earlier passing runs and failed runs are retained rather than counted as evidence for later fixes.

Final validation: 30 visits across six sites in one host/tab/viewer, followed by a separate six-visit quiet run, passed with zero missing source-loaded images, zero settled API errors and zero uncaught viewer exceptions. History, reload, native input and pixel/DOM transitions passed. The final version also passes 34 stylesheet, 12 asset/reconciliation, six mode and seven address checks, plus a 180-second mounted-viewer soak (343 rounds). Quiet-run per-site snapshot p95 ranged from 3.2 to 52.6 ms; capture-work p95 ranged from 0.1 to 20 ms. Local typing feedback measured 9.7 ms p95 on loopback and 15.3 ms with 150 ms added round-trip delay. Concurrent-load measurements include snapshot latency tails near one second; these local results are not a latency guarantee. The stable port-8898 demo serves the checked viewer and passes ten consecutive Facebook snapshots.

Current journey commands, scope and diagnostics are documented in `mirror/README.md`. Final evidence is indexed in `mirror/target/navigation-hardening-evidence.json`; per-visit results retain timings, images, errors, heap and screenshots. Authenticated public-site workflows, arbitrary-site fidelity, and Voidcraft workspace allocation are not established by these local journeys.

## Native rendering and performance

The original per-element computed-style capture was replaced by original stylesheet capture and native cascade evaluation. Source DOM caches, immutable Scala subtrees and viewer scalar patches preserve unchanged content. Input acknowledgement no longer requests a full document rebuild; source-rejected edits still reconcile when the authoritative revision does not change.

The 600-row capture fixture passes the original under-10-ms p95 target with 100 measured samples. The changing-text/timestamp fixture measured 37 ms p95 source timestamp to viewer animation-frame observation over 101 samples, within the proposed under-50-ms local overhead target. These bounded-workload targets are separate from fail-safe capture ceilings: 250 ms for an initial full snapshot, 100 ms for incremental capture, and a one-second renderer deadline for hostile code.

The catalog loads all twenty images. Its compared viewport has mean absolute channel difference 1.122/255 from the source. The Scala Wikipedia article now renders natively with all eighteen images and no source scripts in the viewer; its compared viewport differs by 0.000862/255. Comparisons exclude an eight-pixel frame edge and twenty-pixel right scrollbar region. The large article measured 13.6 ms p95 incremental capture work over eighty samples; it is outside the bounded fixture's under-10-ms result. Viewport similarity and the exercised journeys do not establish arbitrary-site compatibility.

## Integration and boundaries

Voidcraft retains its existing proxy/direct modes and adds the remote browser option. Chrome runs on the selected user's machine through voidcraft-client; the central backend routes authorized operations. One client host represents several real tabs. The implementation preserves account/machine/host-generation identity, source execution, profiles, agent control and screenshot recording.

Client-owned SOCKS5 policy checks destinations, ports and resolved addresses. Real Chrome tests cover navigation, redirects, resources, workers/service workers, WebSockets, TLS, loopback bypass, WebRTC UDP suppression, fail-closed shutdown and connection admission limits. This is browser-network enforcement, not an OS process sandbox.

The real Hero–Meg interruption test previously found an EOF liveness bug. Transport cleanup and ten-second heartbeats with a thirty-second stale threshold now remove stale routing in approximately 30–40 seconds, with connection identity checks preserving replacements. The stylesheet native rerun passed with the same Chrome/tab/JavaScript state and agent grant after reconnect; stale routing was removed after 39 seconds. The isolated Meg runner and native client were confirmed stopped. A later final-UI rerun exposed concurrent viewer-detach/sharing release of the same lease; release is now atomic and idempotent and cannot clear a replacement lease. Native verification of that fix passes all 27 application checks, all 26 tools, backend restart and all eight Hero–Meg checks. Temporary Meg processes were again confirmed stopped.

Linux native packaging uses GraalVM's compatibility target and a baseline Debian C toolchain. Workstation C startup objects otherwise imposed x86-64-v4; the publishing script rejects that requirement. This does not establish macOS/Windows, older-glibc, Safari/iOS or Firefox acceptance.

Explicit boundaries remain audio/device forwarding, DRM, arbitrary custom editors, pre-existing closed roots, constructed font buffers and continuous source drag feedback. CSS animations run locally and can drift. Pixel fallback controls the same tab but does not provide missing device facilities. Redaction covers configured subtrees/attributes and nested frame payloads; privileged source evaluation/screenshots remain full-source operations.

The user's complete test follows engineering completion; it is not a substitute for unfinished implementation or verification. No production rollout, commit or push is part of this work. Existing Voidcraft services and normal clients remain untouched. The isolated integration build excludes the unrelated DemoDriver.scala recorder mismatch.

## Verification provenance

The final capture regression suite contains 24 checks, including detached shadow reattachment, redaction removal, document-base URL changes and capture timing. The unit suite passes 32 tests, including source HTTP redirects/cache validation/range-assembled blobs, stalled-resource cancellation and concurrent lease-release regressions. The host-engine manifest is `mirror/target/native-css-source-manifest.json`. The host/capture workload is unchanged and passes the complete 7,200-second run: 111,136 snapshots, no errors, retained heap from 9,679,424 to 9,965,592 bytes. Later changes are `demo.html` for narrow-window controls and `MirrorControl.scala` for atomic, idempotent lease release; `mirror/target/final-source-manifest.json` records the final source. The UI passes desktop/narrow smoke and a 2,315-round viewer soak (623,448 bytes source heap growth and 91,096 bytes viewer heap growth after warmup). The control scheduler passes a separate ten-minute run with retained heap increasing from 3,609,368 to 4,143,928 bytes; endurance output is `mirror/target/{endurance-native-css-verified,viewer-endurance-final-ui,control-endurance-final}`. The final evidence index is `mirror/target/native-css-verification.json` (status `passed`). Earlier interrupted and pre-architecture runs are not counted as final passes.

Current rendering/workflow artifacts are under `mirror/target/{stylesheet-acceptance,public-journey-acceptance,page-probe,visual-baseline,input-baseline,latency-acceptance}`. Native application artifacts are under the isolated Voidcraft checkout's `target/{browser-mirror-native-check,browser-machines-check,browser-restart-check}` and have been refreshed with passing results. The actual Voidcraft backend/client compile. A missing Fabric codec import in concurrently edited LiveKit configuration was repaired without changing its logic. The full client run exposed two stale policy expectations; both were corrected to retain private-network restrictions and malformed-policy browser disablement, and the affected test reruns pass; the latest client test gate executed twelve tests successfully. Four optional live-provider tests in the broad run were canceled for missing opt-in configuration.

## Audited design history

The text below records the pre-implementation design and early checkpoint. Current status and evidence are in the ledger above.

Reviewed 2026-11-01. This design guided a RoboBrowser-owned implementation, not a port of an entire Node application.

## Outcome and scope

Add an optional `com.outr:robobrowser-mirror` module that projects a Chrome tab hosted by a user’s voidcraft-client into an interactive frontend iframe. Website JavaScript, authentication, navigation, and authoritative state stay in Chrome. The viewer reconstructs DOM and styles and sends authorized input back through CDP.

The product requirement is native-feeling pages rendered by the local browser, with website execution and management in headless Chrome on the selected user machine, controlled through CDP. Immediate local typing, selection, caret movement, scrolling, and sharp native text are release requirements for supported workflows. Ordinary HTML websites are the first target. Arbitrary-site fidelity is a separate, much larger compatibility effort. Do not promise that DOM mirroring fixes canvas applications, DRM, or every third-party widget.

Implement the JVM library in Scala 3 using existing `rapid.Task`, Fabric, Reactify, and Spice conventions. Package browser-native JavaScript capture and viewer resources with the library, as RoboBrowser already does for injected scripts. These resources are part of our implementation, not a dependency on a Node gateway, Preact, Playwright, or rrweb at runtime. Node may be a development test tool. A browser DOM agent must execute in the browser; a later Scala.js port can replace these resources without changing the wire contract. Scala.js is not required for the first module because it would introduce a new build and platform-dependency surface before the core protocol is proven.

The integration target is Voidcraft web tabs as an additional backend alongside its existing proxy. The current Voidcraft working tree has been inspected read-only; it contains concurrent changes. No running service or production deployment was modified.

## Product experience contract (user clarification)

The viewer is an interactive browser surface. It must feel locally rendered and locally responsive while the server owns website execution. The client runs our trusted viewer code and native browser behavior; it does not execute the visited website's JavaScript. Server Chrome owns website JavaScript, cookies, requests, navigation, validation, and final state. CDP is the control interface, not the rendering engine.

For supported controls, let the local browser perform native text editing, selection, caret movement, copy, and scrolling immediately. Forward the corresponding intent exactly once and reconcile source updates without unnecessary caret resets, lost edits, duplicate changes, or scroll jumps. Source changes such as validation and navigation still incur network latency. Define ownership per interaction rather than blanket cancellation: local editing/selection/scroll can proceed; links, submissions, and website actions execute on the server. Hover/focus styling must be coordinated with the source where it triggers website logic.

The authoritative-only vertical slice remains useful to prove correctness, but is not product acceptance. The native-interaction milestone must demonstrate the local interaction model under realistic network delay before the release gate can pass. If arbitrary controls cannot be reconciled, declare a narrower supported workflow set or explicit fallback; do not silently redefine native-feeling interaction as remote keystroke playback.

Headless Chrome is the baseline deployment and acceptance target. DOM mode must work without Xvfb or a headful browser. A headful compatibility mode may be offered for particular sites, but its success does not establish headless acceptance.

Measure local key-to-paint and scroll response separately from authoritative application-result latency. Include native selection/copy, mobile keyboard/IME where supported, controlled inputs, delayed validation, nested scrolling, and reconnect during editing. Compare these against the same page running directly in the local browser as well as the existing remote streaming baseline. The source website remains authoritative even when immediate local feedback precedes its response.

## Voidcraft integration contract — supersedes centralized hosting assumptions

The browser runs on the user-selected machine under `voidcraft-client`. “Server-side execution” means remote from the viewing browser; it does not mean Chrome hosted by the central Voidcraft backend. A phone can view and control a browser running on the user's desktop through Voidcraft.

```text
Voidcraft web tab / trusted viewer
             ↕ rendering, input, tab state
Voidcraft backend: authorization, tab registry, routing, bounded relay
             ↕ authenticated client connection
voidcraft-client: BrowserHost (one managed Chrome process by default)
             ├─ real tab A: RoboBrowser control + mirror session
             ├─ real tab B: RoboBrowser control + mirror session
             └─ real tab C: RoboBrowser control + mirror session
                    ↕
              websites from that machine

Agent tools → backend authorization/routing → same client BrowserHost/tabs
```

One process per client is the default, with a dedicated managed profile rather than the user's ordinary desktop-browser profile. Tabs share that profile's cookies/storage by default. Separate browser contexts can provide isolated sessions later; incognito, separate accounts, and process resource limits may justify exceptions to the single-process default. Chrome itself uses multiple OS processes internally.

Add a browser-host/context/tab ownership API to RoboBrowser before implementing the integration. The existing `RoboBrowser.apply` launches Chrome and `dispose` destroys its owned process: it must not be used independently for every remote tab. Preserve the existing convenience API; introduce an owning host and non-owning tab handles. Separate close viewer, close tab, close context, and stop host operations. Handle popups/new targets, tab close/crash, process crash, history, target replacement, and viewport ownership explicitly. A process failure affects all its tabs; report that scope and do not silently reload authenticated workflows.

Stable routing identity includes account, client instance, browser-host generation, context, logical tab ID, CDP target, and document generation. Persist logical metadata separately from transient target IDs. Switching the selected machine creates or selects a different browser session; it does not migrate live JavaScript, credentials, or tabs automatically. No automatic reroute to another machine after disconnect. Keep Chrome alive through transient client-to-server disconnects where possible; reconnect reconciles inventories and leases before accepting input. Closing a viewer detaches it; explicit tab close destroys the target. Define bounded idle retention and client-shutdown cleanup before shipping.

### Current integration traced in the live working tree

Paths below are relative to `/home/mhicks/projects/clients/voidcraft`; current code takes precedence over early sections of `docs/url-tabs-plan.md` and the legacy `server/` tree.

- `ide/.../ide/WebTab.scala`: current direct/proxy/reading views, machine picker, local agent tab IDs, robot sharing toggle, address/history state, and proxy lifecycle. Add a remote-browser mode behind a common tab-backend interface, preserving existing modes.
- `backend/.../app/AppServer.scala` (`OpenProxy`): explicit machine selection is owner checked; unspecified selection currently uses `getClientForUser`, not workspace routing. For the new mode, specify precedence: explicit tab machine, otherwise conversation/workspace machine via `MachineRoutes`, otherwise the user's default connected client. Existing tab identity always pins its machine.
- `backend/.../webproxy/{ProxySessions,ProxyServer,ProxyRewriter}.scala` and `client/.../webproxy/ProxyFetcher.scala`: current client-side HTTP fetching and cookie jar with backend rewriting. New mode executes the website in real Chrome on that client, retaining original website origins. DOM assets still need a client-context resource path through the bounded relay; central backend must not refetch them using its own network identity.
- `backend/.../tool/web/WebTabTools.scala`, `webproxy/WebTabRelay.scala`, `ide/.../ide/WebTabAgent.scala`: current tools go through an app with the conversation open, wait for a user-matched reply, and enforce sharing in the app. Preserve tool semantics while routing remote-mode execution directly to the client browser. The app becomes a viewer/control surface, not the authority that must execute each agent command.
- `shared/.../api/app/Protocol.scala`: `WebTabInfo`, `WebPageView`, command/reply DTOs need backend/session/generation identity and capabilities. Legacy numbered element IDs must map to the corresponding source snapshot and reject stale references.
- `core/.../api/ClientProtocol.scala`, `client/.../ClientApp.scala`, and `backend/.../service/client/ClientConnectionManager.scala`: extend the existing authenticated outbound client tunnel. Separate long-lived mirror subscriptions from finite request/reply commands. Window chunks and prioritize control/cancellation so assets cannot starve tools, heartbeats, terminals, or model traffic. Handle older clients with capability negotiation.
- `client/.../ClientApp.scala` legacy `browserRemoteStart/Action/Stop`: start takes a one-shot shell screenshot, action only acknowledges, and stop does not own a live CDP browser. These are not an implemented remote session API to reuse as-is.
- `backend/.../sigil/remote/MachineRoutes.scala`: enforce owner/workspace routing and guest restrictions at discovery and execution. Tab sharing authorization must move into backend/client-enforced grants for remote mode; a frontend robot icon alone cannot authorize direct tool execution.
- `build.sbt`: the client's RoboBrowser dependency is currently commented out. Validate dependency versions, JVM packaging and GraalVM native-image launch/CDP/resource compatibility early. Do not infer native-client readiness from a JVM demo. Preserve the independently running development client.

### Agent access is a first-class delivery requirement

The same running source tab must support list/open/read/navigate/close, numbered-element click/type/select/hover/drag/find/wait, keyboard and coordinate input, scroll, JavaScript evaluation/manipulation, screenshots, console/network inspection, resizing, uploads/downloads, and the existing recording workflow. Inventory each actual tool with its result semantics and capability status. Screenshots come from the existing source target, never a freshly loaded URL or a screenshot of the mirrored approximation. Console/network data also comes from that target. Agent evaluation executes in the source page context and remains subject to Voidcraft's existing tool authorization; “no page scripts in the viewer” must not disable agent JavaScript access.

Mutating tools, navigation, and human interaction share the session scheduler and control lease. Observation may continue during human control where authorized. Cancellation stops pending waits/queued work promptly; it cannot undo actions the website already accepted. Define background operation without an open app as an explicit remote-session capability, preserving sharing grants and revocation independently of viewer lifetime. Existing upload confirmations and account-file access remain enforced; transfer authorized files to scoped temporary client files with cleanup rather than allowing arbitrary remote paths.

Integration acceptance must include two clients for one account, another account that cannot access either, multiple tabs with shared login state, agents and a human viewing the exact same changes, takeover and cancellation, viewer detach/rejoin, client reconnect, backend restart, browser/tab crashes, and live-tab screenshots/evaluation. Test LAN/localhost access from the selected machine under an explicit client network policy; proxy-fetch checks do not automatically constrain Chrome's own requests. Multi-tab host ownership and full tool parity are core milestones, not optional post-release enhancements.

## Checked-out references

All four repositories were cloned with `--depth 1` into `/tmp/robobrowser-dom-research`. These are local research checkouts, not vendored dependencies or submodules. `/tmp` is temporary; the pinned revisions below make the review reproducible. No upstream install, build, test, demo, or deployment scripts were executed.

| Project | Local directory | Reviewed commit | License |
| --- | --- | --- | --- |
| [Remote Browser](https://github.com/mystxcal/remote-browser) | `/tmp/robobrowser-dom-research/remote-browser` | `2c9fb2a8ba89def4f655bccc7739bb598e95a740` | Apache-2.0 |
| [PhantomStream](https://github.com/fullselfbrowsing/PhantomStream) | `/tmp/robobrowser-dom-research/phantom-stream` | `7ea468116c22354168c00e8c90fb4f2a0d66270a` | MIT |
| [rrweb](https://github.com/rrweb-io/rrweb) | `/tmp/robobrowser-dom-research/rrweb` | `339683f34bf2dd5df2e7cc18fe59fb924b65661b` | MIT |
| [Shopify Remote DOM](https://github.com/Shopify/remote-dom) | `/tmp/robobrowser-dom-research/remote-dom` | `8d6504993e60433e1366624852cd5b62d20d8bfd` | MIT |

Reproduce by cloning each repository and checking out its listed commit; a shallow clone may require fetching that commit explicitly. Review conclusions below describe these revisions, rather than moving README claims. Keep upstream code out of shipped sources unless deliberately adopted with its required notices. Independent code informed by architectural review does not require copying an upstream application.

## What the code actually teaches us

### Remote Browser: closest assembled system

Primary evidence under its checkout:

- `packages/agent/src/index.ts`: rrweb recorder, cross-origin recording configuration, snapshot lifecycle, and canonical top-level event forwarding.
- `packages/gateway/src/browser/{inject,bridge,agentlink,resolvenode}.ts`: early injection, context-scoped CDP bindings, per-document handles, chunk transport, and node resolution.
- `packages/gateway/src/input/relay.ts`: serialized input, fresh node geometry, key/text/IME/value lanes, and frame coordinate translation.
- `packages/gateway/src/hub/tabhub.ts` and `packages/gateway/src/ws/viewerconn.ts`: canonical snapshot plus bounded delta tail, epochs, ordered replay, and stalled-viewer resynchronization.
- `packages/gateway/src/assets/{fetch,rewrite,stylesheet,route}.ts`: credential-aware resource loading, streamed object URLs, CSS rewriting, and asset delivery.
- `packages/viewer/src/{mirror,rebuild-restore}.ts`, `input/{keys,ime,change}.ts`, and `pipeline/{echo,scroll}.ts`: scriptless replay and the complexity of native edits, local echo, and rebuild recovery.
- `scripts/mirror-diff/`: assembled-browser differential tests, including cross-origin frame/process cases.

Adopt the separation between source agent, authoritative Scala session, inert viewer, and asset resolver. Adopt per-tab capture with per-viewer delivery state. Its local echo uses time windows, including a default one-second editing decay; our correctness contract must distinguish dispatch acknowledgements from application convergence and test reconciliation under delayed source transformations.

Limits found in its own `docs/resource-plane.md`: object-URL ownership currently defaults to the main frame, direct HTTP range fallback is not equivalent to the browser network context, and resource lifetime/redirect handling need more work. The document also records a real composition error where an existing resource rewriter was omitted from the application entrypoint. Component tests alone did not catch that. Reproduce this failure class in our assembled demo tests.

Its backend uses TypeScript, Fastify, Puppeteer CDP plumbing, and WebSockets; the viewer uses Preact and rrweb. Our Scala backend can replace these responsibilities without reproducing its application UI or deployment model.

### PhantomStream: useful modular seams, weaker control guarantees

Evidence: `src/capture/index.js`, `src/renderer/{index,diff,snapshot,sanitize}.js`, `src/adapters/playwright.js`, `src/protocol/{messages,remote-control}.js`, and `src/relay/relay.js`.

Useful ideas: WeakMap node identity, batched deltas, explicit shadow/frame payloads, transport separation, and independent capture/render sanitization. The code is substantial: capture is over 5,000 lines, renderer over 2,000, and a separate injected adapter artifact over 4,000. Avoid maintaining divergent capture implementations for different adapters.

Important correction to broad documentation claims: `dispatchWithCDP` and `dispatchWithPlaywright` in the reviewed adapter dispatch `action.x/action.y`, text insertion, and keys. That path does not establish node-targeted click resolution simply because DOM deltas have stable IDs. Our input contract must carry frame-scoped node identity and validate it on the source.

Its relay drops delivery above a buffered-byte threshold. Snapshot identity alone is insufficient to detect a missing delta within one snapshot generation; use contiguous sequence numbers and explicit recovery. Its default computed-style projection can freeze state, and media-by-reference assumes viewer-side access to resource URLs. Those are poor defaults for authenticated Voidcraft sessions.

### rrweb: compatibility inventory, not an interactive control engine

Evidence: `packages/rrweb/src/record/{index,mutation,observer,shadow-dom-manager,iframe-manager}.ts`, `packages/rrweb/src/replay/index.ts`, and `packages/rrweb-snapshot/src/{snapshot,rebuild}.ts`.

The observer code captures more than DOM mutations: input property setters, stylesheets, adopted stylesheets, selection, scroll, and shadow-root lifecycle. The mutation buffer handles dependency ordering rather than blindly replaying raw observer records. Its cross-origin support requires recording in child contexts; it does not make a parent observer able to inspect another origin.

`enableInteract()` enables pointer events and scrolling in the replay iframe; it does not create a CDP control channel. Snapshot rebuilding has an explicit protected-iframe contract. The unsafe canvas replay branch allows scripts and must not become our fallback strategy.

Use rrweb as a compatibility reference and optional development comparator. The intended shipped protocol and renderer belong to RoboBrowser; adopting rrweb wholesale would reduce initial work but leave its event model and replay semantics as a central runtime dependency.

### Shopify Remote DOM: narrow protocol and receiver validation

Evidence: `packages/core/source/{types,connection}.ts`, `elements/{RemoteMutationObserver,internals}.ts`, and `receivers/{RemoteReceiver,DOMRemoteReceiver,shared}.ts`.

Its connection has small mutation operations and an explicit method-call seam. The observer avoids serializing newly attached descendants twice. Receiver validation checks entire inserted subtrees before constructing host elements, and method calls are policy-controlled. These are useful invariants for our protocol.

It is designed for controlled remote components, not complete arbitrary website state. It supplies neither browser-session ownership nor authenticated assets nor CDP input. `DOMRemoteReceiver` is deprecated in the reviewed source, so it is not a sensible foundation to port directly.

## RoboBrowser integration constraints verified in this checkout

| Existing code | Implication |
| --- | --- |
| `build.sbt`: Scala 3.8.4; `core`, `cdp`, `stream`; Rapid/Fabric/Reactify/Spice | Add optional `mirror` depending on `cdp`; avoid GStreamer dependency. |
| `TabFeatures.eval` / `executeScript` / `callFunction` | Useful basics, but no complete early, isolated, per-frame agent lifecycle. Do not use remote `loadLibrary` as the capture installation mechanism. |
| `CommunicationManager.send` | Requests currently use the one stored session ID. Multi-session send needs a deliberate extension. |
| `WSResponse` | Does not preserve the outer CDP `sessionId`. This must change before multiplexed child-frame event routing. |
| `EventManager.anyEvent` | Can observe unmodeled events; typed channels currently dispatch async tasks, so protocol ordering must be enforced at mirror ingress. |
| `RuntimeEvents` | No typed `Runtime.bindingCalled` channel yet. |
| `RoboBrowser.onDispose` | Hooks run after the CDP session closes. Browser-disposal cleanup must be local; explicit mirror stop can remove live CDP scripts/bindings before disposal. |
| `Mouse`, `KeyFeatures`, `FileUpload`, `Screencast` | Reuse input primitives and optional whole-tab pixel fallback; factor shared input below `stream` rather than depending on its private `InputRouter`. |
| `Screencast` | CDP screencast already acknowledges frames and is change-driven. Do not assume every existing pixel transport continuously emits unchanged frames. |

In particular, do not bolt global auto-attach onto the existing connection without testing root/child event isolation: its current fatal-event handling is not scoped for several child sessions sharing a socket.

## Proposed module and public API

```text
mirror/
  src/main/scala/robobrowser/mirror/
    Mirror.scala, MirrorConfig.scala, MirrorSession.scala, MirrorViewer.scala
    protocol/       versioned Fabric codecs and validation
    cdp/            agent installation, node resolution, frame registry
    capture/        chunk assembly, event ingress, snapshot coordination
    state/          canonical projection, viewer cursors, bounded history
    input/          control ownership, serialized CDP dispatch, edit tracking
    assets/         resource handles, rewrite, browser reads, bounded cache
    http/          optional Spice viewer/asset/WebSocket adapter
  src/main/resources/robobrowser/mirror/
    capture.js, viewer.js, viewer.css
  src/test/         Scala protocol tests and real-browser fixture acceptance
```

Proposed usage, not existing callable code:

```scala
import robobrowser.mirror.*
import robobrowser.mirror.Mirror.mirror

val session: MirrorSession = browser.mirror.start(MirrorConfig()).sync()
// Attach an authenticated viewer through an application-supplied transport.
// Each viewer has its own cursor, queue, role, and lifecycle.
val viewer: MirrorViewer = session.attachViewer(viewerContext, transport).sync()
viewer.receive(clientMessage).sync() // await completion; do not discard the Task
session.stop().sync()
```

Transport abstraction must expose ordered asynchronous send, closure, and bounded delivery/backpressure behavior. A callback returning `Unit` cannot promise delivery. The optional HTTP adapter provides a runnable reference integration and application authorization hooks; it does not own Voidcraft accounts or create a separate identity system.

One session owns one tab projection, capture agent per frame, viewport, and current controller. Several viewers share that capture. At most one driver controls; others observe. The driver can be a viewer or application automation. Application automation must acquire the same control lease and serialize its input/navigation through the session. Raw external CDP commands remain possible but invalidate this exclusive-control guarantee; integration must pause automation during human takeover. Controller transfer releases pressed keys/buttons and invalidates the prior control lease. Explicit stop is idempotent; browser crash/detach promptly closes all viewers and pending mirror work.

## Wire and consistency contract

Use structured nodes, not raw untrusted `innerHTML`, as the primary representation. Preserve document/element/text types, structural placeholders for omitted comments, namespaces, attributes, document shell, doctype, shadow boundaries, and inert nested documents. Text nodes have their own IDs. Custom website elements are reconstructed in the isolated viewer without executing their definitions.

Every source envelope carries protocol version, session identity, frame identity, document generation, snapshot generation, and contiguous sequence/range. Viewer input also carries viewer/control lease identity, action ID, node ID where relevant, and the state generation it addresses. IDs never resolve across a document generation. Unknown major protocol versions are rejected.

Core messages:

- Server to viewer: `Hello`, `SnapshotStart/Chunk/Commit`, `PatchBatch`, `ControlState`, `InputResult`, `FrameState`, `Unsupported`, `Closed`.
- Viewer to server: `Ready`, `AckApplied`, `RequestSnapshot`, `RequestControl`, `ReleaseControl`, `Pointer`, `Key`, `Text`, `Composition`, `Scroll`, and explicit authorized file/dialog actions.
- Patch operations: insert/move/remove node, update text/attribute/property, stylesheet upsert/remove, attach/update shadow or frame document, scroll/focus/selection, and resource-handle changes.

For the first implementation, capture each document synchronously in one JavaScript task: drain pending observer records into the prior generation, serialize using captured native DOM accessors without yielding or calling page getters, discard observer records already represented in that snapshot, then publish the snapshot and subsequent deltas in order. Chunk only the serialized result, not traversal of the live DOM. Abort explicitly at a measured size/time budget. Incremental traversal that yields requires a separate consistency algorithm and is deferred. Cross-frame snapshots are individually consistent, not a globally atomic browser snapshot; gate child attachment by parent frame identity and each child document generation. A viewer renders only a complete generation. Serialize ingress before any async rewriting can reorder it. A missing node dependency or sequence gap requests recovery; never silently continue with a corrupted tree. Moves preserve IDs; removal cleans indexes without breaking same-batch reparenting.

Maintain one canonical snapshot and a bounded tail. Joining/resynchronizing viewers receive an atomic snapshot-and-tail cursor, then live delivery. Per-viewer byte/event/age limits prevent one stalled viewer blocking others. When a viewer falls behind, stop its delta stream and replace its generation once drained. Oversized snapshots use bounded chunks with checksum, timeout, and total-size limits; never silently omit arbitrary page subtrees. Exceeding a configured document budget produces an explicit unsupported/fallback state.

Input acknowledgements distinguish accepted, dispatched, rejected, and failed. CDP accepting a command does not prove application state has converged. A dispatched-input watermark records transport progress only; it does not identify which input caused a DOM mutation or establish that asynchronous validation is finished. Source updates carry observation revisions, not inferred action causality. No universal causal acknowledgement can be derived from arbitrary website JavaScript. Navigation invalidates pending actions for the old document. Retransmitted action IDs are deduplicated within a bounded window; after an ambiguous disconnect, do not automatically retry an irreversible click.

## Capture and styles

Install a bundled agent for current and future documents using CDP new-document scripts, Runtime bindings, and isolated-world evaluation. Validate binding name, execution context, frame ownership, document identity, message size, and chunk sequence. Page-originated capture is untrusted data; a random bridge name is not an authorization boundary.

Use an isolated world for base DOM observation and transport. Main-world hooks may be needed for property setters, `attachShadow`, CSSOM changes, and framework state that isolated-world prototypes do not intercept. Keep those hooks small, installed early, reversible, and separately tested; record their data through a validated bridge. Existing closed roots may be inaccessible. Do not claim a hook observes roots created before installation.

Observe the document shell, open roots, and supported same-origin frame documents. Batch at paint cadence with a maximum latency timer so background throttling cannot stall delivery indefinitely. Normalize records using final DOM membership and dependency order; cap capture work per batch and recover explicitly on overflow.

Prefer original stylesheet structure plus ordered CSSOM/adopted stylesheet updates over computed-style inlining. Preserve pseudo-elements, media queries, font faces, and URL resolution. Capture input values/checked/selection, nested scroll, focus, and supported live properties separately. For inaccessible stylesheets, fetch through the resource plane or report a documented fallback; do not enumerate every computed property on every element by default.

Keep source viewport and viewer layout viewport equal in CSS pixels. Fit the whole viewer by scaling its container; do not independently reflow it to a different viewport. One controller selects viewport; passive viewers scale it. Resize creates a coordinated layout/state update. Hover, focus, scrollbars, device scale, fonts, and animation timing need explicit fixtures. CSS animations run locally and may drift; support pause/sync or report that limitation rather than claim frame-exact parity.

## Input and editing

Resolve `(frame, document, node)` on the server immediately before dispatch. Validate existence, visibility, live rectangle, and hit target before pointer down. Use relative offsets within the node, not a remembered screen rectangle. Correctly translate nested frame coordinates; reject detected stale/occluded targets. Hit testing and CDP dispatch are separate operations: layout can change between them, so node identity cannot guarantee an atomic node-targeted click. Record mismatches and fail conservatively; do not substitute synthetic DOM click events and claim equivalent native semantics. Preserve drag/pointer-capture semantics rather than retargeting every movement as a new click. Coalesce pointer moves only where button and ordering semantics permit.

Reuse CDP mouse/key/text APIs. Keep keydown/up, text insertion, composition updates, selection, shortcuts, and clipboard actions distinct to prevent double insertion. Serialize controller actions; release held keys/buttons on blur, control loss, disconnect, and stop. Paste/upload/download must be explicit application-controlled operations, not unrestricted access to server files or clipboard.

The first correctness milestone uses authoritative rendering without optimistic edits. Cancel viewer default actions for forwarded edits, checkbox/select changes, links, forms, and wheel scrolling, so they do not happen twice. Preserve local text selection/copy only where it does not conflict with remote selection. IME needs a trusted outer-runtime input/composition sink when native editing inside the mirror is suppressed; prove this in an early fixture rather than assuming key events suffice. Treat local scrolling and scalar text echo as independent experiments after authoritative input passes delayed-network tests. A dispatch acknowledgement alone cannot reconcile asynchronous website transformations. During correctness development, use authoritative mode as the reference. The finished viewer uses local native behavior for supported controls with an explicit pending-edit model and reconciliation policy; unsupported controls enter a visible degraded mode. Local echo must account for unacknowledged edits, source transformations, focus transfer, and rebuilds. Do not suppress source changes for a fixed timeout and call that convergence. If server-side formatting or asynchronous validation conflicts with pending local edits and cannot be reconciled, stop prediction for that control and rebuild from authoritative state. Responsive local editing and scrolling are prerequisites for the product release on supported workflows. Authoritative-only interaction is a development/reference mode and an explicit degraded fallback, not the intended finished experience. Start contenteditable with authoritative state; native local rich-text editing needs a separate operation/selection model. Record unsupported behavior clearly until that work passes real editor fixtures.

## Resource plane and viewer boundary

Each resource handle is scoped to session, document/frame context, policy, and lifetime. The viewer receives gateway handles, never server cookies, credential headers, or arbitrary upstream proxy URLs. Browser-context reads are the preferred lane; use captured responses where valid and context-aware loads otherwise. Model refetch limitations explicitly: one-shot URLs, service-worker responses, referrer-sensitive resources, and frame-owned blobs are not ordinary public HTTP files.

Rewrite resources before inserting nodes or styles into a live viewer document, including `srcset`, SVG references, CSS `url()`/imports, fonts, posters, nested frames, and blob URLs. Use a real CSS parser/tokenizer with escape and nesting support; choose and verify a suitable JVM library during the resource milestone rather than implementing URL replacement with regex. Resolve nested CSS against each stylesheet's final URL. Support bounded streaming, cancellation, deduplication, private cache partitioning, and invalidation. Redirects, ranges, upstream validators, and blob ownership have explicit tests. A browser network fallback cannot silently become a direct unauthenticated HTTP request.

Host the viewer runtime on an application-approved isolated origin, with a same-origin inner projection iframe sandboxed without scripts. The trusted outer runtime accesses its inner document and communicates with Voidcraft through validated `postMessage` origin/channel identity. Keep visited DOM out of Voidcraft's own document and custom-element registry. Enforce equivalent restrictions on reconstructed nested documents. The packaged demo must exercise this actual topology.

Validate/sanitize structured nodes and every later patch before DOM construction or property assignment. Disable source scripts, handlers, active embeds, external navigation, form submission, refresh directives, unsafe URL schemes, and arbitrary method calls. Source CSP and cookies do not become viewer headers. Configure restrictive CSP and resource routing so the viewer cannot contact visited origins directly. Test forbidden fetches during construction, not only after rendering. The gateway resource policy must prevent cross-session leakage and unauthorized network destinations; the embedding application supplies allowed egress/private-network exceptions intentionally.

Password contents stay masked in capture, snapshots, traces, and logs. Any local typing prediction is restricted to the controlling viewer and must not republish plaintext. Performance telemetry contains counts, durations, bytes, and reason codes, not typed text or DOM payloads.

## Frames, media, and fallback

Start with the top document and same-origin frames. Before claiming cross-origin support, preserve CDP session IDs, add session-targeted send, scope fatal events, implement recursive child-target attachment and lifecycle, and test OOPIF process swaps. Site isolation stays enabled. Child frame mappings and resource handles die with the owning document/context. Session-level wire sequence and frame-local node identities must remain distinct.

Represent unsupported cross-origin frames and existing inaccessible closed roots explicitly until captured. Injecting into a child target is an intentional browser-privileged capability, not something a parent `MutationObserver` supplies. Cross-origin instrumentation must also work for frames that share a renderer and produce no OOPIF target.

Canvas/WebGL can begin as throttled image snapshots or a visible unsupported region. Media with browser-local state, DRM, audio, and native popups need separate treatment. Offer an explicit whole-tab `browser.screencast` fallback first, with clear mode state and coordinate input. The existing screencast has one callback slot; fallback must lease ownership or refuse a conflict rather than replace another consumer silently. Later integrate `robobrowser-stream` as an optional adapter for richer pixel regions. Do not add GStreamer/Xvfb to the basic DOM module or enable scripts in the viewer to replay unsafe canvas commands.

## Delivery milestones and acceptance gates

| Milestone | Deliverable | Required evidence before advancing |
| --- | --- | --- |
| 0. Feasibility and demo contract | Standalone demo skeleton, fixture corpus, transport/schema draft, browser-host and native-client packaging probes | Prove bounded consistent capture, native editing/IME approach, isolated-viewer authentication, resource/style projection, and two real tabs in one headless host. Document unsupported cases and baseline measurements. |
| 1. Browser-host foundation | Process/context/tab ownership, browser-level CDP transport, session routing, target lifecycle, cancellation, shared demo control API | Closing one tab leaves siblings alive; process crash closes all pending operations; child-target events never leak across tabs; legacy RoboBrowser convenience APIs retain behavior. |
| 2. Interactive demo | One capture per tab, native DOM viewer, snapshot/patch sequencing, minimal resync, serialized node-aware control, observation/evaluation/screenshot API | Human input and scripted agent changes affect the exact same two tabs. The same demo supports in-process and routed-host transport. No source scripts execute in the viewer. |
| 3. Fidelity and recovery | Resource plane, CSSOM/fonts, open roots, same- and cross-origin frames, bounded queues, late join, reconnect, controller handoff | Authenticated resources and child-frame state converge. Delayed viewers do not block other tabs. Drop/reorder/reconnect/process-swap cases recover explicitly without replaying actions. |
| 4. Native interaction and browser features | Local editing/selection/scroll reconciliation, IME, rich editor fixtures, tab popups/history, dialogs, uploads/downloads, resize, media/fallback | Required journeys pass under delay/jitter. No duplicated or lost edits; usable focus/caret and selection; new targets represented correctly. Native-only facilities have explicit capability results. |
| 5. Agent parity and endurance | Every existing Voidcraft web-tool semantic mapped to the demo API, console/network/recording, lease/cancel tests, resource accounting | Agent and human observe the same running state; pending actions respect revocation and takeover; controlled hours-long multi-tab workload stays within configured resource budgets. |
| 6. Voidcraft adapter | Client-host integration, backend relay/authorization, tab-mode UI, routing and native-client packaging | Two user machines, account separation, background authorized agent execution, frontend detach, backend restart, client reconnect, and explicit machine switching pass through actual application wiring. |
| 7. Release candidate | Required workflow matrix, browser/platform support list, measured usability/fidelity and latency report | Standalone demo and integrated flows pass; every required feature is native or has an explicitly accepted fallback. Production rollout remains a separate application decision. |

The product is a complete practical sub-browser. Milestones are implementation order, not permission to redefine the product as a read-only or restricted DOM viewer. Frames, agent parity, and full tab lifecycle are core requirements. Every feature in the capability matrix must be tested or explicitly documented as unavailable; absence must never be reported as successful execution.

Testing layers:

1. Scala deterministic protocol/state tests: codec bounds/versioning, chunk assembly, sequence rejection, snapshot cutover, reparenting, bounded queues, controller transfer, deduplication, and lifecycle cleanup.
2. Browser DOM tests: actual source and viewer Chrome instances against deterministic local fixtures. Use page-observed events and state, not just HTML equality; include native default actions, CSSOM, IME, rich editors, frames, and resource ownership. Include mounted viewer integration so forgotten wiring is detectable.
3. Rendered checks: screenshot source and viewer at identical viewport/fonts; compare representative regions with declared rasterization tolerances and manual inspection. Structural agreement does not prove rendered fidelity.
4. Fault and boundary tests: malicious node/attribute/CSS/patch payloads, early resource fetches, denied egress, stale node actions, child-target detach, repeated start/stop, blocked CDP calls, and forced stalls.
5. Measured comparison: source-to-viewer application/render latency, input-to-source dispatch and visible result, startup, snapshot/patch/asset bytes, CPU/heap, capture work, resyncs, fallback time, and compatibility outcomes. Separate local echo latency from authoritative result latency.

Use replayable fixture workloads and compare DOM mirroring with the existing CDP screencast and WebRTC stream under identical conditions. Count all assets and media bytes; do not compare DOM metadata against total video traffic while omitting viewer resource downloads. Idle and mutation-heavy cases both matter. Report p50/p95 and capture Chrome/network/hardware configuration. No LAN latency or bandwidth improvement is assumed from README claims.

Proposed initial performance targets, to ratify against milestone-0 measurements: under 10 ms p95 capture-batch work for bounded fixture pages; under 50 ms p95 source-mutation-to-viewer-apply overhead beyond transport on a LAN; no continuously growing queue during a ten-minute workload or stalled-viewer test. These are engineering targets, not current results or universal page guarantees. Readiness also requires a meaningful end-to-end improvement on the actual target journeys; lower metadata bytes alone are insufficient.


## Audit findings and revised implementation gates

Audited 2026-11-01 against this plan, local RoboBrowser communication code, and the pinned reference checkouts. These are design findings, not reproduced runtime defects. The original plan needed corrections before building beyond a feasibility slice.

| Priority | Finding | Resolution / proof required |
| --- | --- | --- |
| High | Input acknowledgement was liable to be treated as application convergence. A delayed validator may change a field after dispatch acknowledgement. | Separate dispatch progress from observed DOM revisions; authoritative editing is the development reference. Test delayed formatting, rejected edits, focus changes, and composition before shipping the required local interaction behavior. |
| High | Snapshot “observe and buffer” did not specify a consistent cut and could duplicate already-serialized changes. | Define single-task per-document capture, observer draining, generation reset, and bounded serialized chunking. A task that exceeds budget fails explicitly. Test reparenting and mutations immediately before/after capture. |
| High | The first interactive milestone preceded node-aware input and basic recovery. | Require serialized node resolution, minimal gap recovery, default-action ownership, and cleanup in the first interactive demo. The fidelity/recovery milestone expands multi-viewer and fault coverage. |
| High | Viewer-only control ownership allowed concurrent RoboBrowser automation to fight human input. | One application-enforced driver lease covers both automation and viewers. Human takeover pauses automation, clears queued old-lease actions, and releases held input. |
| High | A separate viewer origin lacked an authentication/bootstrap contract and could rely on blocked third-party cookies. | Use a parent-origin allowlist and short-lived single-use bootstrap ticket, exchanged over exact-origin postMessage. Bind viewer/resource authorization to the session; avoid ambient third-party-cookie dependence. Test blocked third-party cookies, replayed tickets, wrong parent origin, and expired/revoked asset handles. Final HTTP adapter details must pass milestone 0. |
| High | Full node-tree exposure changes the information boundary compared with a video viewport. Masking password inputs alone does not hide hidden inputs or page secrets. | Exclude scripts, comments, hidden-input values, inline handler bodies, and known token/credential attributes before transport; provide configurable subtree/attribute redaction. Preserve layout where possible. Explicitly document that a DOM viewer is authorized for projected document content, including offscreen text, rather than only visible pixels. Do not claim generic secret detection. |
| Medium | Main-world hook cleanup could overwrite another consumer's hooks, and existing/new documents were not distinguished. | Share one agent per tab/document; reference-count users, restore only descriptors still owned by that agent, and handle pagehide/pageshow, BFCache restoration, document replacement, and execution-context destruction explicitly. Do not globally disable BFCache to avoid lifecycle work. |
| Medium | Node hit testing was phrased as a guarantee against wrong-target clicks. | Document the hit-test/dispatch race and validate moving/transformed targets; a source-node ID is a guard, not an atomic native click primitive. |
| Medium | “Small JavaScript resources” understated the browser-side engineering and a full rewrite's cost. | Treat capture and viewer as first-class tested components. Use modular sources with one generated/versioned injection artifact and cross-language golden protocol fixtures. Record build/version checks; do not maintain duplicate injected capture implementations. |
| Medium | Platform acceptance was unspecified, and Chrome-to-Chrome tests do not prove mobile frontend compatibility. | First declared support: desktop Chromium viewer. Add actual Safari/iOS and Firefox acceptance only if required by the selected Voidcraft journeys; desktop viewport emulation alone is not that evidence. |

Additional lifecycle requirements: reserve observation-only domains/listeners without disabling domains another feature owns; bound mirror RPC deadlines and cleanup on timeout; do not leave timed-out CDP callbacks or listeners retained indefinitely. Existing `CommunicationManager.send` has no request timeout and the event path logs raw receive payloads on decoding errors. Add sanitized diagnostics for capture bindings and redact source/edit payloads even on malformed input. Test these failures without placing secrets in logs. New CDP timeout behavior must preserve existing request semantics and have its own regression coverage.

No automatic mode switch may replay pending input. Switching DOM/pixel mode starts a new projection generation, cancels old queued actions, releases held keys/buttons, and enables input only after the replacement view is ready. Apply the same readiness gate after reconnect, navigation, viewport reset, and controller transfer. Background watchers cannot choose a new source viewport.

Milestone 0 exit artifact: a concrete list of supported target journeys and exclusions, measured streaming baseline, executable feasibility fixtures, and explicit pass/fail decisions on the four high-risk probes (snapshot cutover, native editing/IME, authenticated isolated viewer, and stylesheet/resources). A failed probe revises the design before module-wide implementation. Full performance and arbitrary-site compatibility remain unproven.

## Standalone RoboBrowser demo — required proof before integration

Deliver a runnable, documented `sbt "mirror/Test/runMain spec.MirrorDemo"` (implemented; see `mirror/README.md` for current scope) and an automated `mirror/Test/runMain spec.MirrorAcceptance` entrypoint. Both run without Voidcraft, accounts, production credentials, or application services. Provide a local fixture server and a headless browser host with one dedicated disposable profile. Bind the demo to loopback by default; a deliberately enabled remote-host mode uses authenticated encrypted transport and an explicit host identity.

The demo UI includes tabs, address bar, back/forward/reload, viewport control, connection state, control ownership, and explicit DOM/pixel mode state. It can open arbitrary user-entered test URLs as well as deterministic fixtures. The source is headless Chrome. A second actual browser renders the viewer; inspecting its DOM must prove native text/content rendering. Closing the viewer and reconnecting returns to the existing source tabs. Provide explicit Close tab and Stop browser controls and clean up only demo-owned processes/profiles.

Expose a typed control API plus a small local HTTP adapter and scripted agent driver. The driver must list/open/read/navigate/close, target elements and coordinates, type/compose/select/drag/scroll, evaluate page JavaScript, wait on conditions, capture the live tab, inspect console/network, resize, transfer files, and drive recording. It uses the same authoritative tab service as human input. A demo automation pane may show action results, but tests must also drive the API independently of the viewer UI to prove agents can work while no viewer is attached. There is no need for a paid model or LLM to prove this contract.

Run the same suite in two configurations:

1. In-process host for fast debugging.
2. Host in a separate JVM process through an outbound authenticated connection to the demo relay, with session discovery and transport reconnect. This models voidcraft-client; the relay must not launch Chrome or silently fetch source assets itself. Allow a second machine to run the host for manual network validation. Never expose raw CDP externally.

The routed configuration is mandatory before Voidcraft integration. Inject latency, jitter, queue saturation, chunk loss at the application layer, disconnects, and stale messages. Use a slow passive viewer alongside an active viewer; keep control traffic responsive during a large asset transfer. Test 0/50/150 ms added RTT and report the exact simulation method. Check local interaction feedback independently of source application latency. Network capture and counters include all DOM, asset, and fallback traffic.

Fixtures cover multi-tab shared login, SPA navigation, traditional forms, source-side validation, contenteditable, IME, nested scrolling, CSS/adopted stylesheets/fonts/pseudo-elements, open shadow roots, same/cross-origin frames, popups, dialogs, file input/download, canvas/WebGL/media, large DOM churn, and an intentional hostile projection. Run a small representative real-site corpus manually as well; synthetic success alone is insufficient.

Acceptance produces a machine-readable result file, per-feature outcomes, source/viewer captures, sanitized event traces, latency/byte/heap measurements, and explicit reasons for unsupported or degraded cases. Desktop/mobile rendered inspection supplements automation. A demo that starts is not a passed demo. Require source-state assertions, visual checks, native-input checks, and agent-command outcomes on the same session. The demo is kept in the repository as the ongoing regression harness rather than discarded after integration.

## Operational contracts tightened by the second audit

- **Browser endpoint and sessions:** use browser-level CDP discovery (`/json/version`) for the host; the existing `/json` page discovery is insufficient as the sole host abstraction. Multiplex request IDs at connection scope and retain outer session IDs; distinguish browser-level commands from tab commands. Recursively attach required child targets. Preserve legacy page-connection behavior through an adapter and regression tests.
- **Ownership and permissions:** model context/tab/profile ownership independently of a viewer. Store authoritative agent sharing grants server-side with owner, tab, generation, scope, and revocation version. Re-check grants at dispatch on both sides of the tunnel. A client reconnect establishes a new transport generation; old queued commands cannot run. Pending actions are invalidated on lease transfer. Pure observations may run concurrently; arbitrary evaluation is potentially mutating and uses the mutation scheduler.
- **Isolation:** source browser tabs can share a profile; viewer origins must not thereby merge untrusted page content across accounts/sessions. Prefer per-session viewer origins or an equivalent proven isolation boundary. The trusted runtime owns inner projection documents; website CSS must not style host controls. Treat main-world agent code and bridge messages as untrusted observations, not proofs of permission or resource ownership.
- **Profiles:** dedicate a persistent profile to the paired account/client identity, lock it against concurrent hosts, and keep it local. Explicit ephemeral contexts are separate. Re-pairing to a different account cannot inherit credentials automatically. Process restart may restore URLs/profile state but does not restore arbitrary live JavaScript; signal the new host generation. A reconnect to a still-running process preserves live state.
- **Resource policy:** apply allowed-network policy to browser navigation and subresources as well as mirrored asset reads. Specify enforcement coverage for redirects, DNS resolution, WebSockets, workers and service workers before claiming parity with proxy network restrictions. CDP observation alone is not an egress firewall. Test the actual enforcement mechanism on the user's machine. Viewer relay/storage is not end-to-end private merely because cookies stay on the client; document where rendered content transits and keep traces opt-in/redacted.
- **Bounded work:** configure host tab/context limits, snapshot sizes, per-viewer and per-client queues, resource caches, in-flight asset bytes, RPC deadlines, and idle retention. When capped, reject explicitly rather than evicting a live tab. Bootstrap defaults must be recorded and exercised at their limits. Fair scheduling spans tabs and shares the client tunnel with other Voidcraft workloads.
- **No implicit input retry:** mutation requests have bounded idempotency records and generation-scoped IDs. A timeout/disconnect can produce an unknown outcome; do not report success or re-execute. Resync source state before the next action. Long evaluation/waits have cancellation semantics and bounded output; cancellation may stop waiting without undoing already executed JavaScript.
- **Element references:** agent read results carry snapshot/document/frame identity. Never reinterpret an old numbered element as a newly allocated one after navigation. View IDs are not automatically CDP node IDs. Transforms, nested frames, zoom and CSS pixels require explicit coordinate conversion and fixtures.
- **Native behavior coverage:** define supported/degraded/unavailable behavior for browser permission prompts, OAuth popups, passkeys/WebAuthn, clipboard permissions, drag/drop, audio/video/DRM, fullscreen, downloads and platform file dialogs. These may require host-side interaction or explicit handoff. A pixel fallback does not solve device permissions or DRM. Report limitations without claiming universal browser equivalence.
- **Tool parity:** the acceptance manifest enumerates every registered `WebTabTools.all` tool and its schema/result/effect/cancellation behavior. Include account-file upload consent, screenshots from the live tab, `record_web_tab`, navigation history, console and network limits. Every entry maps to a tested client-host handler; acknowledgement stubs fail the suite.
- **Release readiness:** first publish the library/demo as experimental until the declared complete-workflow gate passes. Integrate as an additional Voidcraft mode. Keep proxy mode available and never silently migrate existing proxy cookies or sessions into a remote profile.

## First implementation change

Begin milestone 0 with the multi-tab browser-host ownership and client packaging probes above, then the browser-host foundation and interactive demo: module skeleton, versioned protocol, one-document capture, strict isolated viewer, a Spice demo, and real Chrome acceptance fixtures, including minimal sequence recovery, serialized node-aware input, and disposal from day one. Keep the public transport seam and lifecycle contracts correct from that first slice. Do not begin by translating thousands of upstream lines or by modifying Voidcraft production.

Remaining design spikes belong to their milestone: bounded snapshot capture under mutation; CSS parser choice and difficult resource fetches; multi-session CDP routing; edit watermark semantics; and capture/fallback ownership. Each spike ends with an executable fixture and a recorded decision. This plan has established source-level design evidence only; it has not yet established runtime compatibility or performance for any upstream project or for the proposed implementation.


## Implementation checkpoint — 2026-11-01

The first executable host/viewer and separate-JVM host transport are now in `mirror/`; run instructions and explicit limitations are in `mirror/README.md`. The development HTTP harness uses the JDK HTTP server, not Spice. Browser ownership and the backend interface remain transport-independent. Existing `RoboBrowser` entrypoints were left intact. No Voidcraft services or source files were modified.

Implemented evidence includes a shared-profile multi-tab host, native sandboxed DOM projection, revision patches/full resync, node-generation guards, serialized source input/agent commands, image resource reads, source screenshots/pixel mode, and an outbound loopback host relay. This is a feasibility implementation: computed-style polling, replacement text editing, and a single full-control capability do not meet the final capture, editing, isolation or lease contracts above. Milestone 0 is not closed: stylesheet/resource, IME, hostile-content, performance/baseline, native-client packaging and complete workflow gates remain open. Later milestones must not be marked complete based on these fixture tests.

Validation at this checkpoint: 21 standalone real-browser assertions passed, including native pointer/text input, an authenticated image unavailable directly to the viewer, pixel/DOM transitions, popup discovery, scroll synchronization, revision recovery and source-state retention after reload. The separate-JVM relay/host acceptance passed. All 10 existing `spec.RoboBrowserSpec` tests passed. Source and viewer screenshots were inspected. These are local Chromium fixture results, not performance, hostile-site, cross-machine or production acceptance.


## Compatibility checkpoint — 2026-11-01

Added a deterministic shopping journey suite, exercised through both the local and separate-JVM backend: 20 checks in each configuration. Fixes cover select initialization after options, multi-select changes, edit/acknowledgement snapshot races, composition protection, native Enter submission and textarea newlines. Capture now preserves basic SVG geometry, gradient backgrounds, and before/after generated content, including live CSSOM updates. Pointer hover drives source CSS state. The original 21 browser checks and relay control acceptance continue to pass.

A public catalog rendering probe records source/viewer screenshots, missing images and capture sizes without declaring compatibility. The measured catalog fixture loaded all 20 images and projected no script elements. HTTP gzip reduced the complete JSON response from about 1.19 MB to 38.4 KB, excluding images and headers; this does not reduce browser-to-host CDP capture work. Capture was 23.1 ms in one sample. Ten observed source mutation timings had medians about 166 ms locally and 168 ms via a separate JVM, including API dispatch and a 40 ms observation poll. The 150 ms viewer polling loop remains a latency floor. See `mirror/README.md` for precise scope and artifacts. Event-driven capture, deduplicated styles, full resources/frames and lease semantics remain open gates.
