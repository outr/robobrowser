# robobrowser
[![CI](https://github.com/outr/robobrowser/actions/workflows/ci.yml/badge.svg)](https://github.com/outr/robobrowser/actions/workflows/ci.yml)

Headless Browser wrapper library providing lots of features for API-access

## Running a real (non-headless) browser with no visible window

Chrome's headless mode is fingerprintable, and some sites — anything behind
Cloudflare Turnstile, for example — block headless browsers outright. Even with a
valid captcha token, a headless browser can be served empty content or get wedged
on a challenge page. When you hit that, you need a *real* headful browser.

But "headful" does not have to mean "a window on your screen". A headful Chrome
renders to whatever X display it's given; point it at a **virtual display** and it
runs fully headful (so bot-detection is satisfied) while painting off-screen, with
no window anywhere. On Linux the standard tool for this is **Xvfb** (X virtual
framebuffer).

Configure a headful browser:

```scala
RoboBrowser.withBrowser(RoboBrowserConfig(
  browser = Browser.Chrome,
  browserConfig = BrowserConfig(headless = false) // real browser, not headless mode
)) { browser =>
  // ...
}
```

Then run your process against a virtual display so no window appears.

**1. Install Xvfb**

```bash
# Debian / Ubuntu
sudo apt-get install xvfb
# Arch / CachyOS
sudo pacman -S xorg-server-xvfb
# Fedora
sudo dnf install xorg-x11-server-Xvfb
```

**2. Start a virtual display** (`:99` is arbitrary — any unused display number):

```bash
Xvfb :99 -screen 0 1920x1080x24 &
```

**3. Launch your app pointed at it.** Chrome inherits `DISPLAY` from the JVM that
spawns it, so setting it on the parent process is enough:

```bash
env DISPLAY=:99 sbt "runMain com.example.MyApp"
```

That's a real headful Chrome rendering to `:99` — invisible, but indistinguishable
from a browser with a monitor.

### Minimized / occluded windows drop input

A headful Chrome window that is **minimized or fully covered** is treated as occluded:
Chrome backgrounds the renderer, throttles timers, and drops synthetic keystrokes and
mouse events. Typing appears to do nothing until the window is restored. Guard against
it with `disableBackgrounding = true`, which sets `--disable-backgrounding-occluded-windows`,
`--disable-renderer-backgrounding`, and `--disable-background-timer-throttling`:

```scala
BrowserConfig(headless = false, disableBackgrounding = true)
```

Running under Xvfb (above) sidesteps this entirely — a virtual display has nothing to
minimize or cover — which is one more reason to prefer it for unattended automation.

### Notes

- Only `DISPLAY` controls visibility here; keep `headless = false`. Setting
  `headless = true` re-enables the mode bot-detection blocks, regardless of `DISPLAY`.
- On a machine with a real desktop session (`DISPLAY=:0`), a headful browser opens a
  visible window. Use Xvfb (a separate display like `:99`) to keep it off-screen.
- For a long-running or scheduled job, start Xvfb from a systemd unit (or a wrapper
  that starts it if not already running) and export `DISPLAY` before launching.
- `xvfb-run` is a convenience wrapper that does steps 2–3 in one command
  (`xvfb-run --auto-servernum sbt "runMain ..."`), where packaged — it ships with
  Debian/Ubuntu's `xvfb` but not with Arch's `xorg-server-xvfb`.

## Captcha solving

`robobrowser.Captcha` provides universal captcha detection and solving (reCAPTCHA
v2/v3, hCaptcha, Cloudflare Turnstile) via a pluggable `CaptchaSolver`
([`AntiCaptchaSolver`](cdp/src/main/scala/robobrowser/Captcha.scala) is the default):

```scala
import robobrowser.Captcha.*

browser.solveCaptcha(antiCaptchaApiKey) // detect -> free auto-pass, else solve & inject
```

It first waits briefly for the browser to self-issue a token (Turnstile/reCAPTCHA
"managed" mode often passes real browsers for free), and only calls the paid solver
if none appears. Note the headless caveat above: for Turnstile-gated content a
solved token still requires a *real* browser to render the result.
