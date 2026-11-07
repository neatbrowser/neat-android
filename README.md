# NEAT Search

A mobile browser in the style of Arc Search, built on **GeckoView** (the Firefox engine, shipped inside the app).
The ad, tracker and cookie-banner blocking comes from the lists of
[neat-filters](https://github.com/neatbrowser/neat-filters); how they reach the engine is decided in step 4.

Licensed under GPL v3 (add `LICENSE` to the repository root).

## Why GeckoView and not the system WebView

The system WebView is updated by the phone, not by the app. On a phone whose Android version Google no longer
supports, or one without Play services, it stops getting security fixes, and a browser opens any site. GeckoView
is part of the APK, so every release carries a current engine whatever Android version the phone runs. The price:
a bigger APK, a release at least every ~4 weeks to pick up Gecko's security fixes, and some sites made only for
Chromium may work worse. It also gives what WebView cannot: WebExtensions for blocking, and a real private mode.

## Status: step 2 of 5

| Step | What | State |
| --- | --- | --- |
| 1 | App skeleton, GeckoView, home screen, search panel, one page | built by CI |
| 2 | Tab switcher, many tabs, history, Pin, ghost mode (Gecko private sessions) | **written, not yet built** |
| 3 | Page menu (Find, Share, Zoom, Desktop Site) and Site Settings | planned |
| 4 | Blocker: pick the route (see below), list updates, debug screen of blocked requests | planned |
| 5 | Measure blocking against NEAT for Windows on a set of pages | planned |

What steps 1 and 2 do:

- Home screen: NEAT gradient and logo; bottom bar with tabs, "+" and settings (settings only shows "Coming soon").
- "+" slides up a search panel and opens the keyboard. Text that looks like an address is opened, anything else is
  searched on Google. Only `http` and `https` can be typed in; other schemes become searches. What you open always
  goes into a **new tab**.
- Under the box: **Pinned** pages, then **History**; typing filters both (accents and case ignored, so "doc bao"
  finds "Đọc báo"). Every row has a button to remove it; History has "Clear". Suggestions come from the local
  history only; nothing is sent anywhere while you type.
- The tab button opens the **tab switcher**: a big card per tab, swipe sideways to move, swipe up (or tap ×) to
  close, tap to go to it. "Clear All Tabs" closes them all with a few seconds to Undo. Cards show a picture of the
  page, taken when you leave it (kept in memory only).
- On a page the bar's right button becomes an up-arrow that opens a menu: back, forward, site, copy link, reload,
  and **Pin** / Unpin.
- The eyes button (in the search panel and in the tab switcher) switches **ghost mode**. Ghost tabs are Gecko
  private sessions; they are never written to disk, never recorded in history, cannot be pinned, and the search
  panel does not show pins or history while ghost mode is on. The window is marked secure in ghost mode: no
  screenshots and a blank card in the recent-apps list. Ghost tabs are gone when the app is closed.
- Open tabs, history (last 2000 pages) and pins are kept in small text files in the app's private storage
  (`tabs.tsv`, `history.tsv`, `pins.tsv`). Reading them back accepts only `http`/`https` addresses.
- Links from other apps open here in a normal tab (the app can be chosen as the browser).
- Back is the system default; an open panel closes first.
- If a page's process crashes, a fresh session reloads the same address. Load errors show a simple page and Reload
  retries the address that failed. `target=_blank` links open a new tab.

Not yet, on purpose: Find, Share, Zoom, Desktop Site and Site Settings (step 3); the blocker (step 4);
full-screen video, downloads, permission prompts, sign-in pop-ups that need `window.opener`; a way to go back to the
home screen while tabs are open (it shows when no tab is open).

## Blocker routes (step 4, to be decided by measuring)

GeckoView has no request-interception callback like WebView's `shouldInterceptRequest`. The routes:

1. **Gecko's own content blocking** (Enhanced Tracking Protection): built in, uses Mozilla's lists, not neat-filters.
2. **A WebExtension** bundled in the app. Either uBlock Origin (GPL v3, same licence as this app), or an extension
   of our own that applies neat-filters with adblock-rust built for WebAssembly or reached through GeckoView's
   native messaging (per-request latency must be measured).

Also to check in step 4: what Gecko sends to Mozilla and Google by default (tracking-protection list updates,
Safe Browsing) and whether it can be switched off.

## Build

Android Studio Panda 3 (2025.3.3) or newer opens the project as is. From a shell, with JDK 17 and the Android SDK:

```sh
./gradlew :core:test            # logic tests, no Android needed
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/
```

GeckoView comes from `https://maven.mozilla.org/maven2/` (release channel, `org.mozilla.geckoview:geckoview`).
The version is pinned in `gradle.properties` (`geckoviewVersion`). GeckoView 159 needs `compileSdk` 37, which needs
AGP 9.1.1 and Gradle 9.3.1, so those are what the project uses; moving GeckoView forward may raise them again.
The build keeps only `arm64-v8a` and `armeabi-v7a`.

GitHub Actions (`.github/workflows/build.yml`) does both on every push and attaches the debug APK to the run
(Actions → the run → Artifacts → `neat-search-debug-apk`). Install it on a phone with "install unknown apps" allowed.

## Layout

```
core/   plain Kotlin, no Android: address-or-search decision, URL helpers, tabs, history, pins, file format (JUnit tests)
app/    Android: Compose UI, tab/session controller, storage
tools/  gen_icons.py: launcher icon layers and logo from NEAT's default2000.png
```

The logo is `browser/branding/official/default2000.png` of the NEAT repository. To regenerate the icon files:

```sh
python3 tools/gen_icons.py /path/to/neat/browser/branding/official/default2000.png   # needs Pillow
```

## Notes

- GeckoView sends its own Firefox user agent; no user-agent changes are made.
- Cleartext `http` is allowed because a browser has to open such sites.
