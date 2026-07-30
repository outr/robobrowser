# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RoboBrowser is a Scala 3 headless browser automation library built on the Chrome DevTools Protocol (CDP). It provides API-level access for browser automation, web scraping, JavaScript execution, and network interception. Published to Maven Central as `com.outr:robobrowser-cdp`.

## Build Commands

```bash
# Build all modules
sbt compile

# Build only the cdp module
sbt cdp/compile

# Run all tests
sbt test

# Run the main test suite (this is what CI runs)
sbt "testOnly spec.RoboBrowserSpec"

# Run a specific test class
sbt "testOnly spec.SomeSpec"
```

**Requirements:** JDK 25 (Zulu distribution used in CI), SBT 1.12.0. A Chrome/Chromium browser must be installed for tests.

## Module Structure

- **root** - Aggregates `core` and `cdp`
- **core/** - Core module (dependency for cdp, currently has no source files)
- **cdp/** - All source code lives here. Chrome DevTools Protocol implementation

All source is under `cdp/src/main/scala/robobrowser/`. Tests are in `cdp/src/test/scala/spec/`.

## Architecture

### Browser Lifecycle

`CDP` (static object) → launches a browser process with `createProcess()` → queries available debug targets with `query()` → connects via WebSocket with `connect()` → creates a `RoboBrowser` instance representing a single tab.

`BrowserConfig` has 40+ options controlling browser launch (headless, GPU, proxy, security, window size, etc.). `Browser` detects installed browsers (Chrome, Chromium, Edge, Vivaldi).

### Communication

`CommunicationManager` handles the WebSocket connection to CDP. It serializes JSON requests via the `fabric` library, assigns incrementing integer IDs, and routes responses back through a `ConcurrentHashMap` of pending callbacks.

### Tab Operations

`TabFeatures` trait provides the main API: `navigate()`, `eval()` (execute JS), `executeScript()` (load JS from resources), `loadLibrary()` (inject external JS), `callFunction()`, `bringToFront()`. `RoboBrowser` extends this trait.

### DOM & Selection

`Selection` wraps CSS selector results with methods: `click()`, `focus()`, `value` (get/set), `getAttribute()`, `innerHTML`, `innerText`, `outerHTML`, `submit()`. `Selector` provides type-safe selector builders (`Query`, `Class`, `Id`, `Tag`, combinators).

### Events

`EventManager` dispatches typed events through `EventChannel`s organized in the `Events` namespace: `target`, `network`, `dom`, `page`, `inspector`, `runtime`, `fetch`. 100+ event case classes in `robobrowser/event/`.

### Input

`Key` sealed trait represents keyboard input (letters, digits, special keys, F1-F12). `KeyFeatures` provides `send()` and `type()` methods.

### Network Interception

`Fetch` enables request interception with `continueRequest()`, `continueWithAuth()`, `fulfillRequest()`, and `failRequest()`.

### Scraping

`RoboScraper` provides queue-based URL traversal with duplicate detection, download handling, and captcha retry. Pluggable via `ScrapeHandler` trait (implementations: `MemoryScrapeHandler`, `JsonWritingScrapeHandler`).

## Key Libraries

- **fabric** - JSON serialization/deserialization (used heavily for CDP messages)
- **rapid** - Async `Task` monad for all operations
- **spice** - HTTP client and WebSocket connectivity
- **reactify** - Reactive `Val`/`Var`/`Channel` bindings
- **jsoup** - HTML parsing (scraping)
- **tika** - Content type detection
- **scribe** - Logging

## Scala Conventions

- Scala 3.7.4 with `-unchecked -deprecation` flags
- Async-first: all operations return `Task[T]` (from rapid library), not `Future`
- Tests use ScalaTest `AsyncWordSpec` with output flags `-oDF` (detailed with timing)
- JVM requires `--enable-native-access=ALL-UNNAMED` and `--add-modules jdk.incubator.vector`
