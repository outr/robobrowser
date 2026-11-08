# Execution-mode readiness

> **Architecture change — 2026-11-05:** #2 (one local JavaScript executor) is the sole browser path in 2.5.0-SNAPSHOT. DOM projection, pixel fallback, and dual-copy execution are removed. Earlier projection and Voidcraft remote-CDP evidence below is historical. See [current architecture](mirror/README.md); frontend disconnect still stops agent execution.

This record separates verified behavior from architecture limits. A public page rendering successfully is not proof of its login, payment, upload, permission or recovery workflows.

## Six-part completion work in progress

The current request covers all six areas below. Passing regression counts do not close these items automatically.

| Area | Required evidence or decision | Current state |
| --- | --- | --- |
| Unified execution | One explicit execution authority, background operation, human/agent state parity, native rendering acceptance | Architecture choice requested; local executor and remote DOM remain distinct |
| Real authentication | User-designated isolated account/profile; login, logout, expiry and provider redirects | Test profile requested; controlled fixtures are not provider acceptance |
| Browser facilities | Original-origin workers/storage, trusted input, explicit device/DRM/WebAuthn support boundaries | 15 controlled remote-host checks passed; device/DRM/WebAuthn and provider acceptance remain open |
| Profiles/integration | Workspace separation, shared sibling tabs, persistence, detached agents and two-machine tunnel verification | Five workspace tests and ten isolated two-machine checks passed with both JVM and freshly rebuilt Linux native clients; full native application and backend-restart checks also passed |
| Reliability/performance | Multi-session failure/reconnect tests; complete process-tree measurements; bounded endurance on the tested revision | Current upload/runtime revision passed 41 navigation cycles over 609.7 seconds with process-tree RSS sampling; no current two-hour run |
| Functional limits | Bounded large-file transport, media compatibility and popup workflow coverage | Native uploads now stream under configurable bounds; remote agent chunking verified at 8 MiB; remaining media/popup/platform limits are not closed |

## Execution authority

| Requirement | Remote native DOM mode | Single local executor |
| --- | --- | --- |
| Website application code executes on the headless host | Yes | No; the frontend executes it |
| Host continues when the frontend disappears | Yes, subject to host lifecycle | No |
| Pointer-driven website JavaScript runs locally | No; input reaches the host | Yes |
| Agent controls the executing document | CDP backend and control protocol | HTTP relay to synthetic DOM operations |
| Chrome profile and remote browser context | BrowserHost API | Separate local-executor cookie/profile store |
| Multiple source tabs | Existing host/tab API | One active document per execution session |

The single executor avoids duplicated application actions because there is only one running copy of the page. It does not silently run a second headless copy to approximate disconnected execution.

## Tested local-executor behavior

- Page navigation, mapped cross-origin links, shell reload, script-driven absolute navigation where cancelable, SPA history URLs, focus retention and source URL visibility.
- Human and agent operations share state. Ordered commands have request IDs and document epochs. Retries do not repeat an action; expired results are not re-executed. Unknown outcomes block further mutations until document replacement.
- Controlled authentication: server login cookies, HttpOnly isolation, script-visible CSRF cookies, script-cookie writes, logout, and fetch/XHR credential modes. Schemeful SameSite handling is conservative; browser-specific exceptions and partitioned cookies are not implemented.
- Optional named profiles: separate origins and storage, persistent-cookie retention, session-cookie expiry, exclusive filesystem leases, service restart and stale-command rejection. Frontend browser storage remains on the frontend; the profile file contains persistent original-origin cookies and origin mappings.
- Site-supplied bearer authorization survives fetch and XHR from mapped pages. Control-service authorization is not automatically copied to resource requests.
- Native POST form submissions, submit-button action overrides, and script-driven form submission stay in the managed session and preserve their request bodies.
- Cross-origin child frames expose agent reads, edits and clicks through document-specific handles. Navigation invalidates old handles; removing a frame withdraws it. The toolbar focus guard propagates to child frames.
- A transient media recovery failure can be retried on the same video element; retries share a bounded session attempt budget. Origin-registration failures report errors instead of falling back to unproxied resource URLs.
- Native downloads preserve attachment filenames and exact HTML/binary bytes; native and agent-selected files upload through the same page handler, with duplicate command retries suppressed.
- Script-opened cross-origin popups preserve bidirectional message origin/source identity and callback cookies. Agents list popup documents and target their current document epoch. Old popup epochs are rejected before dispatch.
- Window WebSocket text/binary/Blob payloads, protocol negotiation, handshake cookies and original Origin, ordered delivery, close codes, and connection-time destination policy.
- Same-node media source changes during recovery: an old conversion cannot overwrite a newer video source.
- Navigation retires the old relay before replacing the document; rejected navigation reconnects the existing page without losing its state. Stale location updates cannot overwrite the saved destination.
- Public GitHub rendering/video, Facebook login-page rendering without the earlier hydration error, and Wikipedia rendering. These tests do not submit real credentials or prove authenticated third-party workflows.

## Remaining boundaries

- This is not transparent origin virtualization. `location`, origin-sensitive code, service workers, workers/modules created through unsupported URL APIs, dynamic base elements, browser permissions, DRM, WebRTC and native-only APIs can differ or fail.
- Script-cookie access uses synchronous requests. WebSocket transport uses HTTP long polling. Remote-network latency affects those operations even though local pointer handlers and video decoding do not require a server round trip.
- Synthetic agent input is not trusted CDP input. Sites that require `isTrusted`, user activation or platform authentication are not covered.
- Script-opened popups with an opener have controlled cross-origin messaging, callback cookies and agent-window tests. This is not third-party OAuth certification. Anchor-created windows without an opener, arbitrary blank-window document scripting, and arbitrary popup hierarchies are not established support. Opening a blank window and then assigning its location is tested. Native browser popup blocking still requires user activation.
- Native downloads and browser-selected multipart uploads are tested. Agent file selection accepts up to 10 files totaling 512 KiB; ordinary gateway request bodies stream with an application-configurable limit (64 MiB by default), including multipart framing. Download management remains the frontend browser's responsibility, not a remote-host download tool.
- Named local profiles are not CDP profiles. They do not move localStorage/IndexedDB to another frontend machine. One profile cannot be opened concurrently by separate execution sessions.
- Reserved gateway routes, frame sandboxing, origin isolation and connection-time destination policy have focused checks, not a production security certification. Arbitrary website JavaScript still executes in the user's frontend browser.
- HEVC recovery fetches a bounded source clip, then streams conversion output into native MediaSource when the original origin/CORS policy permits script access. Other media uses the completed converted file. It is not progressive upstream fetching: the source download still finishes before decoding starts. The source/output clip limit is 16 MiB and duration limit is two minutes. Conversion retains its 60-second host deadline. Earlier whole-file startup measurements below are historical.
- Finite budgets are explicit: origin registrations, active sessions, command history, media conversion, cookies, profile size and socket queues. Exceeding a budget is a reported failure rather than silently dropping data or executing an action twice.

## Evidence

The current run writes:

- `mirror/target/single-execution/result.json`
- `mirror/target/execution-profiles/result.json`
- `mirror/target/execution-sockets/result.json`
- `mirror/target/local-execution/result.json`
- `mirror/target/execution-endurance/result.json`

`running` is not a passing endurance result. The endurance timing is pointer event timestamp to the next animation-frame callback; it does not measure physical display latency. Its retained-heap check covers the parent viewer, not total browser memory. Older native-mode/application evidence predates the local-executor changes and is not a release gate for this mode.

The managed demo remains on port 8898. No deployment to another application is implied by these library tests.

Run `scripts/execution-acceptance --public --endurance-seconds 600` to repeat the current acceptance gate. It records source hashes and retains each run under `.robobrowser-demo/execution-acceptance/`. A failed gate remains failed even when earlier individual checks passed. The wrapper clears only its generated test-output directories before execution, preventing stale artifacts from being mistaken for results of the current run.

## Earlier verified revision: 2026-11-04

The frozen-source gate at `.robobrowser-demo/execution-acceptance/20261104T141834Z/result.json` passed, with all 131 recorded source hashes unchanged:

- 43 unit tests; 64 single-executor checks; 16 profile checks; 12 socket checks; 6 media source-switch checks; 20 dual-copy diagnostic checks. The dual-copy checks demonstrate divergence and are not evidence of synchronized headless execution.
- 613.9 seconds of completed endurance, 43 fixture/GitHub/Facebook/Wikipedia cycles, 1,720 pointer samples, p95 17.2 ms from event timestamp to next animation frame.
- Parent viewer retained heap baseline 672,384 bytes; maximum 676,940 bytes. No page/viewer errors reported by the endurance checks. This does not claim an entirely silent Chrome process log or total-process memory stability.
- Updated port-8898 live smoke passed with source/served-asset parity, profile reopen retention, 95 decoded GitHub video frames over three seconds, and no captured Facebook page errors. Evidence and inspected screenshot: `.robobrowser-demo/readiness-live/`.
- Existing native address-bar and interaction acceptance also passed; the log is retained beside the gate. These checks do not substitute for a full remote-mode or two-machine release test.

Earlier failures remain under the timestamped acceptance directories. In particular, the old media conversion deadline failed on a fresh GitHub load; raising its bounded allowance is recovery hardening, not a claim of instant HEVC startup. Real-account sign-in, third-party OAuth and autonomous remote execution in local mode remain unverified or unsupported as described above; controlled popup workflows have since gained coverage below. This is a tested local-execution preview, not universal browser replacement certification.

## Local file and popup commands

The existing authenticated `agent` API now additionally accepts:

- `{"op":"files","selector":"#file","files":[{"name":"notes.txt","type":"text/plain","base64":"aGVsbG8="}]}`. An empty list clears the input. All files are validated before replacing its selection; `input` and `change` fire afterward. No host filesystem path is exposed.
- `{"op":"windows"}` returns script-opened child window IDs, current document epochs, URLs and connection state.
- `{"op":"windowCommand","windowId":"...","windowEpoch":"...","command":{"op":"read"}}` targets that popup document through the same ordered, deduplicated parent agent relay. Popup navigation invalidates its epoch. In-flight loss is reported as an unknown outcome, not automatically replayed. Child windows close when their owning document leaves.

`ExecutionFilesAcceptance` and `ExecutionPopupAcceptance` are included in `scripts/execution-acceptance`. Their evidence directories are `execution-files` and `execution-popups`.

The agent API also accepts `frames` and `frameCommand` with `frameId`, `frameEpoch` and a nested `command`, following the same lifecycle rules as popup commands. Native browser origin restrictions still apply to site code; the agent channel does not give the website direct cross-origin DOM access. The form, iframe and media-retry regression artifacts are `execution-forms`, `execution-frames` and `execution-media-retry`.

Unused cross-origin HTML links now resolve through a navigation redirect rather than allocating an origin immediately. A 100-domain link fixture passes with an eight-origin budget and follows a deferred link into the same managed session. `LocalExecutionExperiment.maxOrigins` defaults to 4,096 and is configurable (1–16,384); the demo harness exposes `executionOriginLimit`. Resource registration failures remain explicit. This replaces the old fixed 64-origin limit that broke Wikipedia when silent direct-URL fallback was removed.

After an unacknowledged child-document mutation, retries are not replayed and new mutations remain blocked. Nested read-only inspection remains available, with a bounded nesting depth, so an agent can observe the actual popup/frame state before deciding how to recover.

Agent reads now include controls in open shadow roots and script-created closed roots captured by the startup bridge. `selectorPath` descriptors returned by `read` can be passed unchanged to `click`, `type` and `files`; ordinary CSS selectors still work. Nested roots, outer input-event listeners, file selection, retries and removed paths have controlled coverage in `ExecutionShadowAcceptance`. Closed roots remain closed to the website's ordinary `shadowRoot` getter. Parser-created closed roots and roots created before instrumentation are not established support. Read traversal is bounded at 10,000 visited elements and 200 returned controls.

## Follow-up investigation history

The expanded pre-shadow revision passed the frozen gate at `.robobrowser-demo/execution-acceptance/20261104T161933Z`: 43 unit tests, 204 browser checks, and 35 navigation cycles over 616.7 seconds. The next revision passed 216 browser checks but its endurance run failed during a GitHub video operation after ten completed cycles (`20261104T164240Z`). That failed run remains failed. A diagnostic rerun completed 21 cycles over 314.1 seconds; this does not establish that the intermittent failure is fixed.

Failure captures now retain the executing document state, video readiness/error details and a screenshot. CDP evaluation failures include the actual JavaScript exception. Navigation evidence records readiness timings and media fetch/conversion timings. A live smoke also exceeded its GitHub video deadline once and passed on retry; real-account acceptance remains pending. These observations prevent a claim of complete readiness.

Run `scripts/execution-live-check.mjs` against the running managed demo to repeat the isolated live profile, agent, public-page, media and served-source checks. It requires Node 22+ and Chrome (`CHROME_BIN` may override the executable), uses a disposable profile, and leaves other active sessions alone. Its result is marked running before checks and failed on error, preventing an earlier success from masking a later failure.

## Final frozen gate for this follow-up: 2026-11-04

`.robobrowser-demo/execution-acceptance/20261104T171940Z/result.json` passed with `sourceUnchanged: true`:

- 43 unit tests and 216 browser checks, including 10-bit HEVC recovery after a transient upstream failure.
- 41 complete fixture/GitHub/Facebook/Wikipedia cycles over 603.5 seconds; 1,640 pointer samples; event-to-next-animation-frame p95 17.3 ms.
- Parent viewer retained heap baseline 672,860 bytes; maximum 684,928 bytes. This is not total browser-process memory.
- Median tested-page readiness: GitHub 1,610 ms, Facebook 844 ms, Wikipedia 417 ms. GitHub's first load was 6,927 ms, including 133 ms fetching media and 4,592 ms converting it; subsequent visits reuse the session cache. Readiness predicates are page-specific, not full network-idle measurements.

The codec retains the network-isolated sandbox, 1 GiB address-space ceiling, CPU/file limits and conversion deadline. Limiting glibc arenas allows up to four decoder threads within those limits. Repeated controlled clip conversions measured 3.5–5.0 seconds with four decoder threads versus 7.1–7.7 with two. Explicit `yuv420p` output avoids converting 10-bit HEVC into H.264 that the frontend still cannot decode. Timing varies with host load; this remains whole-clip conversion before playback, not streaming transcoding.

The earlier GitHub evaluation failure did not recur in the subsequent 314-second diagnostic, 601-second timing run, or this 603-second final run. Its cause was not established; it is not labeled fixed. The earlier cold-start timeout also remains recorded. A finite passing gate cannot establish universal site compatibility or authenticated third-party acceptance.

The updated port-8898 live check also passed at 17:31 UTC; its result, inspected GitHub/Facebook/Wikipedia screenshots and log are retained under the same gate's `live/` directory. It verified served-source parity, profile reopen retention, file selection, popup control and closed-shadow control. Cold GitHub video readiness was 6,751 ms (152 ms fetch, 4,845 ms conversion), with 95 decoded frames over three seconds. Facebook controls were ready in 1,025 ms; Wikipedia readiness plus the intentional three-second settling period was 3,699 ms. Twenty agent-read samples measured median 2 ms and p95 3 ms on this local host; these are small-sample request timings, not a WAN guarantee. No captured page errors occurred on the three checked public pages. The demo remains on the existing port and token.

## Progressive recovery and connection lifecycle follow-up

The replacement-page handshake now survives a delayed leaving notification from the old page. Retired message handlers cannot detach a new connection, and a status poll from an old connection cannot report an error against its replacement. An attachment acknowledged after the viewer has detached is explicitly released instead of staying available until its lease expires. `ExecutionHandshakeAcceptance` forces these event orderings; both the lost-handshake and delayed-release cases failed before their fixes.

HEVC conversion emits fragmented MP4 chunks while retaining the decoder sandbox. A native MediaSource buffer uses the known duration and supports normal seeking and looping. Source replacement, removal and page teardown cancel the frontend reader and revoke its object URL. Ordinary compatible resources remain native resources. Cookie-bearing cross-origin media and resources without suitable CORS use completed-file playback. Converted endpoints suppress wildcard CORS for credentialed responses; a native fetch from an uninstrumented child realm verifies that the converted bytes cannot bypass that restriction. A metadata probe also handles HEVC-plus-AAC files that Chrome otherwise plays as audio with no video error. Legitimate audio-only files are detected as not needing recovery.

`ExecutionProgressiveAcceptance` covers audio/video playback before conversion completion, stable duration, seeking, looping, original URL visibility, removal, source replacement, CORS and credentialed fallback, audio-only inspection, and a fifth distinct recovered clip in one session. `ExecutionMediaBufferSpec` covers incremental reads, cancellation, failed conversion, reader limits and cache reservation failures.

The former lifetime limits of four clips and twelve attempts are replaced by a bounded cache and rolling admission policy. Completed, unpinned output is evicted as needed; an existing resource ID reconstructs its output on demand. Limits remain explicit: 32 MiB of cached/reserved output per session, at most four concurrent recovery attempts, twelve new attempts per rolling minute, eight stream readers per output, and 128 retained resource metadata entries. Conversion reserves its maximum output allowance before starting, then releases the unused portion. Source input buffers, transient compaction copies and browser decoder memory are separate from the output-cache allowance. `ExecutionMediaCacheSpec` verifies six sequential clips, reconstruction of evicted bytes, active-reader protection and rate-window expiry.

The earlier intermittent GitHub failure remains historical evidence with an unconfirmed cause. The forced connection races now have confirmed reproductions and fixes; they are not presented as proof of that older failure's exact cause. The final frozen-source and live results for this follow-up are recorded below when complete.

The first frozen progressive/cache revision passed at `.robobrowser-demo/execution-acceptance/20261104T190549Z`: 51 unit tests, 249 browser checks, and 40 navigation cycles over 605.9 seconds. Its 1,600 pointer samples had event-to-next-frame p95 17.5 ms; parent viewer retained heap was 675,244–679,840 bytes. Three fresh live profiles passed, with cold GitHub video readiness of 4,482, 4,170 and 3,558 ms. Their conversions took 7,430, 7,919 and 11,014 ms, respectively: video playback began before full conversion completed. These are host-load-dependent samples, not a universal latency guarantee.

A subsequent small-chunk transport fix explicitly flushes progressive HTTP output after each available batch. An actual HTTP client regression receives its first byte while the producer is still unfinished. Final-source evidence for this last change follows below; the preceding ten-minute result belongs to the pre-flush revision.

## Final source verification: progressive recovery and cache follow-up

`.robobrowser-demo/execution-acceptance/20261104T192429Z/result.json` passed with all 143 recorded source hashes unchanged: 52 unit tests, 249 browser checks, and 19 complete navigation cycles over 305.7 seconds. The final run recorded 760 pointer samples with event-to-next-frame p95 18.4 ms; parent viewer retained heap was 675,244–679,840 bytes. The separate preceding ten-minute run is identified above.

Three fresh disposable profiles passed against the restarted port-8898 demo. Each verified served-source parity, profile retention, file selection, popup and shadow controls, native video playback, and public Facebook/Wikipedia rendering without captured page errors. Final-run screenshots were inspected. Results and screenshots are retained under this gate's `live-1/`, `live-2/`, and `live-3/` directories.

| Fresh run | Navigation to GitHub video ready (ms) | Recovery start to first decoded frame (ms) | Server conversion (ms) | Frames decoded over three seconds |
| --- | ---: | ---: | ---: | ---: |
| 1 | 3275 | 1320 | 4690 | 91 |
| 2 | 2244 | 681 | 2998 | 90 |
| 3 | 2966 | 1174 | 6823 | 90 |

Every live run used progressive playback and produced its first frame before server conversion completed. The intervals have different starting points; navigation includes page loading and script startup. Three local samples are not a percentile distribution or a WAN guarantee. The latest demo keeps the same port and access token. Real-account flows and the execution/origin/platform limitations described above remain outside this verification; there is no blanket 100% compatibility claim.


## Optional media inspection regression: 2026-11-04

A valid AAC-only MP4 padded with a valid 17 MiB `free` box reproduced a false application playback error: the native video element played its audio, but the optional missing-video inspection exceeded the conversion input budget and dispatched an `error`. The bridge now records `inspection-unavailable` with its reason when optional inspection fails before confirming an unsupported codec, without reporting a native playback failure or changing the source. A later actual playback error can still retry recovery. Confirmed conversion failures and required recovery failures remain errors.

`ExecutionProgressiveAcceptance` now has 26 checks, including continued oversized-audio playback and a genuinely invalid media source that still delivers one application error. The failing pre-fix and passing post-fix logs are retained in `.robobrowser-demo/execution-acceptance/20261104T223707Z/`.

The frozen gate in that directory passed with all 143 source hashes unchanged: 52 unit tests, 254 browser checks, and 21 complete navigation cycles over 303.8 seconds. Its 840 pointer samples had event-to-next-animation-frame p95 17.1 ms; retained parent-viewer heap baseline/maximum was 675,440/679,840 bytes. These are local browser-path measurements, not physical display latency or total browser-process memory.

The managed demo was restarted on the same port 8898 and passed a fresh disposable-profile live check (`live/result.json`): served source matched, profile reopening and agent file/popup/shadow controls passed, GitHub video became ready in 2,143 ms and decoded 90 frames over three seconds. Recovery-start to first decoded frame was 596.4 ms; source fetch was 135.8 ms and conversion 3,067.3 ms. Twenty agent reads measured median 3 ms and p95 4 ms. Facebook and Wikipedia had no captured page errors. The three screenshots were inspected; GitHub's screenshot shows its public promotional video, not an authenticated session. Real-account/provider login acceptance and the earlier unproven intermittent failure remain outside this passing evidence.


## WebSocket disposal regressions: 2026-11-04

Delayed-response tests reproduced two frontend lifecycle failures: disposing a socket before its allocation acknowledgement failed to release the newly allocated channel, and a pending poll could emit an error after the socket had closed. Inspection also found that graceful close retained server registry entries until a final poll or the 45-second reaper, even when the page had already left and would never poll again.

The relay now has an origin-owned `dispose` operation that removes the channel immediately, wakes any pending poll and cancels the upstream transport. Navigation/failure cleanup uses disposal; ordinary application `close()` retains graceful close semantics. A late allocation acknowledgement triggers disposal, and closed sockets ignore pending responses and remaining events in the same response batch. If the network prevents disposal delivery, the existing expiry still provides eventual cleanup.

`ExecutionSocketsSpec` verifies origin ownership and immediate capacity reuse across 32 open/dispose operations without a final poll. Browser coverage includes delayed allocation, delayed polling and disposal inside an event callback. The failing pre-fix logs and passing targeted log are preserved under `.robobrowser-demo/execution-acceptance/20261104T234243Z/`.

That frozen gate passed: 53 unit tests, 260 browser checks, and five public navigation cycles over 73.2 seconds. The 200 pointer samples measured event-to-next-animation-frame p95 17.3 ms. Parent-viewer retained heap baseline/maximum was 675,428/677,232 bytes. This short navigation smoke is separate from earlier five-minute evidence and does not establish authenticated third-party compatibility.

The updated managed demo passed its fresh-profile live check (`live/result.json`) with served-source parity, profile reopening and agent file/popup/shadow control checks. GitHub video became ready in 2,481 ms and decoded 90 frames over three seconds; Facebook and Wikipedia had no captured page errors. Twenty agent reads measured median 3 ms and p95 4 ms. These are public-page/local measurements; real-account login acceptance remains pending.


## Open/Close lifecycle regressions: 2026-11-04

Delayed-response browser tests reproduced two toolbar failures: Close during session creation allowed the late Open response to resurrect the cancelled session, and an old Close acknowledgement overwrote the status of a connected replacement. The viewer now uses a separate operation lifecycle to invalidate pending opens and close any cancelled allocation when its acknowledgement arrives. Local teardown and Closed status are applied immediately; old server completion cannot overwrite a newer session. Cancellation also releases the viewer's opening lock, and stale operations cannot clear a newer operation's lock or report its errors.

`ExecutionHandshakeAcceptance` adds seven checks covering held allocation/close responses, cancelled allocation cleanup on the server, reopening and replacement status. Both failing pre-fix logs and the targeted passing log are preserved in `.robobrowser-demo/execution-acceptance/20261104T235510Z/`. The full frozen gate there passed with unchanged sources: 53 unit tests and 267 browser checks, including public pages. No new endurance run was included in this gate; earlier duration measurements remain separate evidence.

The updated port-8898 demo passed a fresh disposable-profile live check with served-source parity, profile reopening and agent file/popup/shadow controls. GitHub video became ready in 2,124 ms and decoded 91 frames over three seconds; Facebook and Wikipedia recorded no page errors. Twenty local agent reads measured median 4 ms and p95 8 ms. Evidence is in `live/result.json` beside the gate. Real-account login and universal site compatibility remain unverified.


## Six-area follow-up and integration isolation incident

A new `RemoteBrowserFacilitiesAcceptance` run passed 15 checks against the authoritative host: classic/module workers, service-worker interception, original origin, shared sibling-tab storage, isolated profiles, viewer absence with trusted agent input, native-viewer reconnection, exact 8 MiB chunked file transfer, and persistent cookies/localStorage/IndexedDB/service-worker registration after host restart. This establishes remote-host facilities, not transparent local-executor compatibility or real-provider authentication.

The local gateway now streams ordinary request bodies with a 64 KiB buffer instead of buffering the entire request behind a fixed 4 MiB ceiling. Applications can select `maxUploadBytes` (`executionUploadLimit` in the demo), default 64 MiB and bounded to one GiB. Known oversized requests receive HTTP 413 before upstream dispatch. Body streaming is single-use, detects truncation and enforces its limit for unknown-length requests too. Agent JSON file selection retains its separate bound; the remote host's chunked agent path supports larger files and was verified with 8 MiB.

Linux endurance evidence now samples RSS for the acceptance JVM and descendants every second, separately from renderer heap. Summed RSS double-counts shared pages and can miss sub-second peaks. The pre-upload-change ten-minute run passed 41 cycles over 607.5 seconds with 651 samples and peak summed RSS 4,882,448,384 bytes. It is retained in `.robobrowser-demo/six-area-20261104/memory-endurance/`; it is not a frozen gate for later upload changes.

Voidcraft's five host/workspace tests passed against rebuilt local library artifacts. However, the first attempted two-machine integration run is **aborted, not passed**: its temporary database path overrides did not disable PostgreSQL from the checkout's effective configuration. It connected to local development databases `voidcraft` and `sigil` at `127.0.0.1:5432`, ran startup schema/migration work, and created probe records. Logs include new columns and a member migration reporting 403 rows rewritten and 1,539 members moved. The probe JVM was stopped; its unique remote runner was stopped and verified absent; the normal client services were not deliberately restarted. No migration rollback or database restore was attempted.

Read-only inspection identified test user `BHY7ZuMrLnDviNRPJleTtEcNsvSmrhFa` (`two-machines@local.test`), two client records and two browser-session records. These records and migration impact need review; no broad cleanup or rollback is authorized by this evidence. The aborted log and status are retained under `.robobrowser-demo/six-area-20261104/`. The two-machine harness now validates effective database isolation before `App.init`, rejecting PostgreSQL or database paths outside its temporary root; its JVM-artifact option is explicitly labeled and is not native-image acceptance.

The final local-executor gate `.robobrowser-demo/execution-acceptance/20261105T002140Z/` passed with unchanged sources: 56 unit tests, 270 browser checks and four navigation cycles over 60.6 seconds, pointer-to-next-animation-frame p95 17.2 ms. It includes real 6 MiB multipart upload and configured 8 MiB-ceiling rejection before upstream dispatch. This does not mark all six completion areas achieved.

The corrected two-machine run passed all ten checks using rebuilt JVM clients on the local machine and `meg`. Its effective configuration disabled PostgreSQL before application initialization and used temporary RocksDB directories. Coverage includes machine/profile isolation, sibling tabs, human and detached-agent control, actual tunnel disconnect, no cross-machine rerouting, reconnect with retained tab state, and workspace separation. Evidence is in `.robobrowser-demo/six-area-20261104/two-machines-isolated/`. This is JVM-client evidence, not a native-image release test. Both temporary remote runners were verified stopped.

Containment of the earlier integration incident also revoked the two newly created probe client credentials, matching their IDs, owner and creation timestamps; both updates were confirmed. Probe records remain for review. This containment does not reverse or validate the startup migrations. The launch mistake and database impact remain unresolved independently of the corrected acceptance run. The new launcher and preflight refuse PostgreSQL before application initialization; refusal under the ordinary checkout configuration and acceptance under the isolated configuration were both tested.

The fresh live check against the current port-8898 demo passed with served-source parity. GitHub video became ready in 3,463 ms and decoded 90 frames over three seconds; Facebook and Wikipedia recorded no captured page errors. Twenty agent reads measured median 9 ms and p95 11 ms under concurrent test load. These public-page checks do not establish real-account acceptance.

The current native-rendering suite also completed successfully, including the public-site soak. Evidence is archived in `.robobrowser-demo/six-area-20261104/native-suite/`. Its accelerated 4x video-loop test measured a maximum frame gap of 250.4 ms against 50.1 ms for direct playback; the existing 1,500 ms stall threshold passed, but this does not establish equal smoothness. No current-revision two-hour endurance run was completed in this follow-up.

### Loop timing comparison correction

The earlier 250 ms versus 50 ms comparison used unequal video visibility: the native video began at y=312.8 inside a 200 px-high iframe, entirely below its viewport; the direct video was partly visible. A rerun reproduced the gap. The test now sets matching viewport dimensions, requires the entire video to be visible, and records requestVideoFrameCallback presentation intervals alongside decoder counters. It rejects fewer than 450 presentations in 15 seconds, presentation p95 >=50 ms, or a presentation gap >=500 ms, in addition to its loop/state checks. Instrumentation itself changes decoder scheduling, so this is an instrumented visible-playback test, not proof of uninstrumented offscreen performance.

The corrected run passed five loops on each path, with 826 native versus 804 direct presentations and p95 33.3 ms on each. Maximum presentation gaps were 50.0 ms native and 33.4 ms direct. Evidence is `.robobrowser-demo/six-area-20261104/loop-visible/`. No production playback change was made based on the invalid visibility comparison.

Fresh native client verification passed all ten two-machine checks on the local machine and `meg`. The binary was built with a baseline Linux toolchain and its ELF notes require x86-64-baseline. The assembly embedded all entries of the current locally published RoboBrowser core and mirror libraries without mismatches. Native two-machine evidence is in `.robobrowser-demo/six-area-20261104/native-two-machines/`; this does not certify other operating systems or older libc releases.

The follow-up local-executor endurance run passed 41 cycles over 609.7 seconds, with 1,640 pointer samples and event-to-next-animation-frame p95 17.2 ms. Its 653 process-tree samples measured peak summed RSS 4,923,662,336 bytes; retained parent-viewer heap baseline/maximum was 676,956/681,472 bytes. This run includes the streaming upload/runtime revision and ran alongside other builds and integration tests. It preceded only the subsequent embedded-toolbar visibility change, which is separately checked by application acceptance; it is not a quiet-system latency benchmark or two-hour soak. Evidence is in `.robobrowser-demo/six-area-20261104/current-endurance/`.

The full native-client application probe passed 33 checks and all 26 registered browser tools, including actual rendered application controls, sharing revocation, confirmed file attachment, and workspace isolation. The backend-restart probe then passed with a temporary loopback port reused across both backend JVMs, retaining the native client, Chrome tab, JavaScript state, sharing grant and routing. Evidence is in `native-application/` and `native-restart/` under the same directory. A failed earlier application run exposed a missing frontend bundle in the isolated working directory; the launcher now builds and serves a temporary bundle, and the readiness check requires its main script. A failed earlier restart run encountered a port conflict. These failures are retained, not counted as passing runs.

All three application probes now share a preflight before application initialization, refusing configured PostgreSQL, database paths outside the temporary root, and an external `probe.base` server. The launcher serializes its own probes, avoiding sbt startup collisions. These protections do not undo the previously documented local database migrations.

The final embedded-toolbar follow-up passed 34 full application checks and the 26-tool manifest with native clients. Screenshot inspection found the standalone local-executor demo button in the embedded viewer even though its route was unavailable; embedded mode now hides both standalone demo controls. This is generic embedded-viewer behavior, with no application-specific code in RoboBrowser. Evidence is `.robobrowser-demo/six-area-20261104/native-application-final/`. The native binary predates only this viewer-resource change; the application backend serves the updated viewer and the final browser test verifies it. The remote test runner and client were verified absent on `meg` after acceptance.

External-server refusal was verified with `probe.base=http://127.0.0.1:1`: the preflight failed before application initialization or a network connection. The expected failing log is retained in `isolation-followup/external-server-refusal.log`. Final JavaScript syntax, launcher Python syntax and owned-file whitespace checks passed.


## Anti-Captcha frame handling — 2026-11-05

The visual reCAPTCHA path now shares a generic frame-aware implementation between the legacy RoboBrowser client and managed BrowserTab. It discovers nested and out-of-process iframe documents, maps frame coordinates through scaling and scrolling, crops provider images and explicitly scales them to the provider limit, and checks child-document response tokens. It rejects invalid coordinates and changed frame contexts or geometry before clicking. Provider errors expose only sanitized codes; provider task IDs retain 64 bits. Device scale factor 1 and an entirely visible, unrotated challenge are required. The implementation does not guarantee detection of challenge-image changes within an otherwise unchanged frame.

The final provider suite passed five tests, including secret-safe error reporting and a task identifier larger than 32 bits. Real-Chrome fixture acceptance passed nested opaque cross-site frames, scaled and scrolled coordinates, oversized-image scaling, invalid-coordinate rejection, navigation while waiting for an answer, and the legacy public API. The preceding broader regression run passed 75 CDP tests and 56 mirror tests. These tests use a fake coordinate provider and no real account secrets. Evidence: `.robobrowser-demo/captcha-20261105/final-verification.log` and `regression-suite.log`.

A separately authorized real Facebook login reached its nested Meta reCAPTCHA. Live Anti-Captcha returned coordinates and the browser applied them, but the challenge did not complete and the bounded login wait expired. This is not authenticated acceptance: successful login, on-demand 2FA, and real-account persistence after browser restart remain unverified. No further password submissions or paid provider retries were made after that wait expired. Credentials, provider tokens, account screenshots and profile data remain outside repository evidence. No Voidcraft-specific production code was added to RoboBrowser.


## Human verification handoff — 2026-11-05

Voidcraft now exposes `request_browser_help` for CAPTCHA, two-factor and sign-in handoffs. Its durable conversation card opens the exact existing source tab and initially shows source pixels. Agent observation and input are paused until completion; cancellation and expiry retain the pause. The application owns this workflow. RoboBrowser has only generic viewer changes: isolated embedded-viewer storage, consumed-ticket recovery using the saved grant, initial rendering-mode selection, pasted text/modifier input, and a bounded retry for a definitively rejected stale human lease.

Final isolated application acceptance passed 44 checks and all 27 registered browser tools with the existing native client artifact (November 4). The backend serves the updated viewer resources. Coverage includes rendered chat completion, duplicate opening, iframe and application reload, pasted verification input, source interaction, resumption with an actual page-state check, cross-account refusal, popup-scoped viewers, cancellation, and workspace isolation. A review caught a weak popup-close assertion in the preceding passing run: renewed agent grants lost existing popup scope. Grant renewal now preserves only its already-authorized live tabs, and final acceptance requires both a successful close response and disappearance of that popup. The final run is `95c8dfcb-8b2f-4482-b06e-cca82a5341bf` in Voidcraft's isolated probe directory.

The separate native-client/backend restart probe passed six checks, including a persisted pending handoff that blocks agents after restart and can then be completed. Eight application state/wire tests and eight RoboBrowser control tests passed. JavaScript syntax and affected-file whitespace checks passed. Non-sensitive evidence is retained under `.robobrowser-demo/human-verification-20261105/`; request and completion screenshots were visually inspected. The UI fixture disables welcome-agent turns to avoid unrelated model responses changing tab focus. It invokes actual registered tools but does not establish an autonomous model completing a real login.

These results prove the human handoff with controlled fixtures, not successful real Facebook CAPTCHA/login, real-provider 2FA, or real-account persistence after a Chrome restart. This handoff does not implement retrieval and origin-bound filling of Voidcraft stored login secrets. No real credentials or paid provider attempts were used for these tests. The normal application/client and port-8898 demo were not restarted for this work. Earlier database-migration incident limitations remain unchanged.


## Local execution only — 2026-11-05

Version 2.5.0-SNAPSHOT removes the DOM-projection implementation and its capture/replay assets, pixel fallback, old viewer/control endpoints and projection-specific suites. It also removes dual-copy execution. The demo's root route now serves the local executor directly; its gateway does not create or use a second headless website copy. BrowserHost/CDP, network policy and frame CAPTCHA utilities remain independent. The application-facing entry points are ExecutionGateway and the loopback development ExecutionServer; the artifact/package retains its historical mirror name.

The new gate `.robobrowser-demo/execution-acceptance/20261105T173416Z/` passed with unchanged sources: 28 unit tests, 257 browser checks and four public-site navigation cycles over 63.5 seconds. New checks require retired projection routes to return 404, reject dual execution, and default API opens to local execution. GitHub/Facebook screenshots were retained; GitHub was visually inspected. These results do not certify real-account login or a migrated Voidcraft application.

Voidcraft's migration plan is `docs/robobrowser-integration-plan.md` in its checkout. The old application adapter remains on its older snapshot until migrated; upgrading it without replacing the projection protocol is intentionally not supported. The plan covers client-side resource transport, configurable HTTPS origins, workspace profiles and tabs, the frontend agent relay, same-executor verification and origin-bound secret filling. Local-execution gateway cookies and frontend browser storage are separate; this is not a remote Chrome profile and does not provide unattended execution.


## Persisted Web Storage and IndexedDB — 2026-11-08

Origin-scoped storage checkpoints now live with the gateway profile. A bootstrap document restores native localStorage/sessionStorage and IndexedDB before the original website scripts execute. IndexedDB checkpoints include schema versions, stores, key paths, indexes, records, deleted high auto-increment positions and common structured-clone values. Website storage remains native during execution; mutations and completed write transactions schedule asynchronous snapshots. Explicit toolbar navigation/close flushes the main document and connected child documents. Pending native data survives ordinary reload and can retry synchronization; a fresh frontend can restore only acknowledged checkpoints. The aggregate checkpoint budget is 12 MiB. Unsupported nonportable values and conflicts remain visible errors, not silent conversion or last-writer replacement.

The final controlled gate `.robobrowser-demo/execution-acceptance/20261108T150215Z/` passed with unchanged sources: 31 unit tests, 267 browser checks and a 60-second fixture endurance run. The new storage acceptance destroys both gateway memory and the original frontend, then verifies profile-based restoration before site scripts, schema/structured values, profile/origin separation, pending reload recovery and deletion. Its three small-fixture explicit flush samples were approximately 14.8 ms, 0.3 ms and 9.6 ms; these are not large-database performance guarantees. Internal checkpoint keys are hidden from ordinary Storage enumeration. Generic profile and browser tests remain enabled.

The public gate `.robobrowser-demo/execution-acceptance/20261108T145525Z/` failed at GitHub automatic visible-video playback: decoding succeeded but autoplay did not start. A comparison with the storage restore wrapper removed failed the same assertion; its log is `.robobrowser-demo/storage-sync-20261108/public-autoplay-without-restore.log`. The wrapper was restored before the final controlled gate. No public compatibility pass or real-account login acceptance is claimed for this change. IndexedDB CryptoKeys/nonportable types, Cache Storage and service-worker registration replication are not implemented. Voidcraft's integration plan now describes the storage routes, restore barrier and flush/status contract; its application migration remains separate.
