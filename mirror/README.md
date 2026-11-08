# RoboBrowser local execution

The website executes once, in the frontend browser. Native JavaScript, DOM, canvas and media operate in that document. A resource gateway maps upstream origins and maintains cookies; an authenticated agent relay controls the same live document. The frontend must remain connected for agent execution.

The DOM-projection renderer, screenshot fallback and dual-copy mode were removed in **2.5.0-SNAPSHOT**. The artifact and Scala package retain the historical `robobrowser-mirror` / `robobrowser.mirror` names. The old `LocalMirrorBackend`, `MirrorDemoServer`, projection/control protocol and viewer resources are no longer available. Generic Chrome/CDP utilities remain in `robobrowser-core` and `robobrowser-cdp` independently.

## Try it

```bash
scripts/mirror-demo start
```

The managed demo stays on port **8898**, now with local execution at `/`. Use the printed capability URL. `/execution` is an alias for the same UI. `restart` replaces only the managed demo; it disconnects its sessions. A named profile retains gateway cookies and origin mappings. The demo server no longer starts a headless copy of the website.

## API and application boundary

`ExecutionServer` is the loopback-only development HTTP server. `ExecutionGateway` owns sessions, mapped resource requests, cookies, network policy, WebSocket relay and profile leases. `ExecutionAgentRelay` serializes commands to the frontend and enforces document epochs and bounded retry deduplication. `execution.js` is the trusted parent shell; `execution-bridge.js` runs in the website frame. Control credentials remain outside the website origin.

Publish the local snapshot with `sbt 'publishLocal'`. Depend on:

```scala
"com.outr" %% "robobrowser-mirror" % "2.5.0-SNAPSHOT"
```

The gateway currently maps to `<origin-key>.<session-id>.localhost:<port>` and accepts `HttpExchange`. Production applications need authenticated transport and configurable HTTPS origin adapters. A global development bearer token is not a multi-user authorization system. Applications own user/workspace/client routing, secret access, human-verification UI and lifecycle policy; RoboBrowser has no application-specific allocation rules.

Supported document operations include read/state, click/type/scroll/eval, files, frames/windows and child commands. These use DOM APIs and synthetic events, not CDP trusted input. A disconnected executor cannot act. After navigation, commands must use the newly attached document epoch. Retrying a possibly applied action with a new ID can duplicate it; preserve request IDs and handle uncertain outcomes explicitly.

## State and limits

Gateway profiles persist cookies, origin mappings and acknowledged Web Storage/IndexedDB checkpoints. Website code still uses native frontend storage while running; a fresh frontend restores the saved checkpoint before website scripts execute. This is a gateway profile, not a remote Chrome profile. Named profiles are exclusively leased; sharing one profile across multiple independent execution sessions requires an application lifecycle extension. Closing the frontend does not transfer running JavaScript into remote Chrome.

Public-page compatibility and fixture success do not establish real-account authentication, CAPTCHA compatibility, arbitrary origin-sensitive APIs, DRM, or hardware permissions. There is no automatic headless/projection fallback. Media codec recovery requires its existing ffmpeg/ffprobe and sandbox dependencies; Chrome is needed for browser acceptance, not to run the gateway itself.

## Verification

```bash
scripts/execution-acceptance --public --endurance-seconds 60
```

This runs the current unit/browser suites, including rejection of retired routes and dual-copy mode, profile/cookie persistence, forms/files, frames/popups, shadow DOM, sockets, media recovery, handshake races, focus and single-action retries. Evidence is retained under `.robobrowser-demo/execution-acceptance/`. `scripts/execution-live-check.mjs` separately checks the managed demo using a disposable frontend.

See [current readiness](../EXECUTION_READINESS.md). Earlier projection benchmark and implementation documents are historical, not evidence for the current integration.


## Storage checkpoints

With a named persistent profile, the gateway saves origin-scoped localStorage, sessionStorage namespaces and IndexedDB checkpoints. IndexedDB export includes versions, object stores, key paths, indexes, records and auto-increment positions (including deleted high keys). Structured values include dates, regular expressions, maps, sets, cycles, BigInt, array buffers/views, blobs and files. Nonportable structured values such as nonextractable CryptoKeys are not silently converted: checkpoint failure is reported and local data remains pending. Cache Storage and service-worker registrations are not part of these checkpoints.

The document loads through a restore barrier, then its original scripts run. Ordinary writes are debounced; committed read/write transactions and schema changes schedule an IndexedDB checkpoint. Toolbar navigation and Close await a flush. A native browser reload can recover pending data from the existing frontend's storage. A fresh frontend can restore only the last **acknowledged** gateway checkpoint: killing the frontend/device before synchronization finishes can still lose unacknowledged changes. Closing the viewer does not keep website execution running.

The aggregate checkpoint budget is 12 MiB per execution profile, within the existing 16 MiB profile-file limit. Quota, transport, blocked restoration and concurrent-writer conflicts are explicit failures; a stale snapshot cannot silently replace a newer server revision. There is no general merge of independently modified IndexedDB databases across devices. Snapshotting large or frequently mutated databases costs time and bandwidth; this is bounded checkpoint synchronization, not a continuous database replication engine.

The trusted document bridge exposes `storageStatus` (pending/error/revision) and `storageFlush` (await acknowledgement). Applications must forward `/__rb_execution/storage` POST requests and the bootstrap storage script through the same selected-client resource adapter, preserving exact origin checks and body limits. Do not bypass the restore document when adapting HTML responses. Use `spec.ExecutionStorageAcceptance` to verify a restarted gateway plus a completely fresh frontend, not only reloads in an existing browser profile.
