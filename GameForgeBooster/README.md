# GameForge Booster

A one-tap performance HUD + game launcher for non-rooted Android, built for
Android 10 (API 29) as the floor and API 36 as the compile/target SDK.

## What's actually real here (read this first)

Every "booster" feature claims to do something. On a non-rooted phone, most
of what OnePlus/Samsung's launchers show is only possible because they ship
as **system apps** with OS-level hooks a Play-Store app can never get. This
project is built to be honest about that line:

| Feature | What it really does | Limitation |
|---|---|---|
| Floating HUD (FPS / RAM / thermal) | Live `Choreographer` frame rate of its own overlay window, live `ActivityManager.MemoryInfo`, live `PowerManager` thermal status (API 29+) | FPS is a vsync/jank proxy, not an instrumented in-game counter — no non-root API exposes that |
| Background app trim | Calls `killBackgroundProcesses()` for apps found via Usage Access | A *hint* to Android, not a guarantee — the OS can ignore it, same ceiling MIUI's own "clear memory" hits for 3rd-party apps |
| Cache manager | Reads real per-app cache size via `StorageStatsManager`, jumps you to that app's Clear Cache screen | Android has not allowed 3rd-party apps to clear *other* apps' cache directly since Android 6 — root or system-app only |
| Do Not Disturb toggle | Real `NotificationManager` DND control | Needs one-time Settings grant, same as any DND app |
| Screen recorder / screenshot | Real `MediaProjection` capture, written to app-private storage, shareable via FileProvider | Standard Android capture consent dialog appears each session (OS requirement, can't be skipped) |
| "Virtual HDR" / vivid tint | Real translucent color-tint overlay window (same technique as blue-light-filter apps) — genuinely blends with what's beneath it | Not real HDR. Can't boost contrast/saturation of pixels already drawn by another app without root; true HDR needs an HDR panel + HDR content, neither of which software can add. Your phone's camera "HDR/HDRA/HDR+/AI" labels are the camera ISP's tone-mapping, a different subsystem — doesn't make the *display* panel HDR, and the Redmi 9A's panel spec is standard HD+ |
| Quick Settings "Game Mode" tile | Real toggle, starts/stops the HUD service from the shade | — |
| Add Game (manual) | Full installed-app picker, persists your pick, merges into the dashboard | Auto-detection only catches apps that set `android:appCategory="game"` or the legacy `FLAG_IS_GAME` — plenty of real titles (older Unity/Cocos exports, web-wrapper games, emulators) set neither, so this isn't a rare fallback |
| Auto-arm HUD on game launch | Polls `UsageStatsManager`'s event log (`ACTIVITY_RESUMED`) every ~1s from a background service to detect the foreground app, and starts/stops the HUD automatically | Deliberately **not** built on `AccessibilityService` — Google actively rejects Play Store apps that use accessibility for non-accessibility purposes, so real app-lock/booster apps use this polling approach instead. ~1s latency, not instant |
| Keep screen awake during HUD | Real `FLAG_KEEP_SCREEN_ON` on the visible overlay window — genuinely prevents screen sleep while the HUD is up, no extra permission needed | Only while the HUD is showing |
| Battery Saver warning | Real `PowerManager.isPowerSaveMode()` check + deep link to turn it off | Can't be toggled by a 3rd-party app directly — Battery Saver silently caps CPU/GPU on most OEM skins, which is worth knowing before blaming a game for lag |
| Smooth Display / refresh-rate link | Deep link to system Display settings | GameForge cannot change which refresh rate the system picks — no 3rd-party API for that; it just gets you to the toggle faster |
| Exempt from battery optimization | Real `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` system dialog | Standard Doze/App Standby exemption so the HUD + watcher aren't deferred in the background — same mechanism any legitimate background app uses |

### What I checked and deliberately left out

- **`android.app.GameManager` (API 31+, Android's actual "Game Mode" API)** —
  looked like the obvious thing to wire up, but the permission that lets
  you *set* another app's game mode (`MANAGE_GAME_MODE`) is
  `signature|privileged` — Google Play apps cannot hold it, full stop. A
  non-system app can only read its *own* game mode while it's the one
  running. Since GameForge isn't a game, there's nothing real this hook
  could do for *other* apps, so it's not in the build (an earlier draft had
  a stub here that silently did nothing — removed).

Nothing here pretends to overclock the CPU, force a higher refresh rate the
panel doesn't have, or silently kill other apps' processes outright — those
would require root and aren't implemented.

## Project shape

```
app/src/main/java/com/gameforge/booster/
  MainActivity.kt            dashboard: detected games, quick tools
  PermissionActivity.kt      one screen explaining + linking every permission
  GameRepository.kt          finds installed games via ApplicationInfo.CATEGORY_GAME
  BoosterOverlayService.kt   the floating HUD + HDR tint windows (foreground service)
  ScreenCaptureService.kt    MediaProjection screenshot/recording (foreground service)
  BoosterTileService.kt      Quick Settings tile
  PerformanceModeManager.kt  DND / usage-access / cache / bg-trim logic
  ThermalMonitor.kt          PowerManager thermal status listener
  FpsCounter.kt               Choreographer-based frame counter
  HdrSimulationView.kt        the vivid-tint overlay view
  GameForegroundWatcherService.kt  auto-arms the HUD when a known game opens (UsageStatsManager polling)
  AddGameActivity.kt          manual "Add Game" picker over all installed apps
  ManualGameStore.kt          persists manually-added game packages
  BatteryAwarenessManager.kt  Battery Saver detection, Smooth Display + battery-exemption deep links
```

### Icon

Replaced the single-path placeholder with a layered adaptive icon: a
hex-badge outline (reads clearly at launcher size, any mask shape) with a
bolt mark inside and a small accent dot, plus a proper `monochrome` layer
for Android 13+ themed/Material You icons. Files: `ic_launcher_background`,
`ic_launcher_foreground`, `ic_launcher_monochrome` under `res/drawable/`.

## Building

This zip includes a working Gradle wrapper (`./gradlew`, wrapper jar
included, targeting Gradle 8.9 / AGP 8.6.0 / Kotlin 1.9.24), so it should
build as-is in Android Studio or CI once an Android SDK with
**compileSdk 36 / Build-Tools for API 36** is available — note API 36 is very
new, so confirm your CI's SDK manager / `sdkmanager` packages actually have
it before the first run; drop `compileSdk`/`targetSdk` to 35 as a fallback if
not yet available in your toolchain.

I was not able to run an actual Gradle build inside this sandbox (no network
path to Google's Maven/SDK servers here), so treat the first CI run as the
real compile check — if anything doesn't build, paste me the error and I'll
fix it.

## Next up (not yet built, straightforward to add)

- Per-app cache browser (list + sizes) instead of jumping straight to the
  first detected game's storage screen
- Persist HUD position between sessions
- Settings screen for HDR tint color/intensity instead of fixed defaults
- Battery-drain-rate readout in the HUD (via `BatteryManager` deltas)
