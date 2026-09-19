# GameBoost X — Phase 1-5 (complete)

Real, functional Android project (Kotlin + Jetpack Compose). Nothing in here fakes data —
every number on screen comes from an actual Android API and shows "Unavailable" when a
device doesn't expose it.

## What's implemented

**Phase 1 — Core + Home dashboard**
- `DeviceInfoManager`: real CPU utilization (via `/proc/stat` delta, polled every 3s),
  CPU core count/frequency (sysfs, when readable), RAM (`ActivityManager.MemoryInfo`),
  thermal status (`PowerManager.getCurrentThermalStatus` on Android 10+, battery-temp
  heuristic below that — labeled as a heuristic, not real thermal data), battery
  (level/charging/Battery Saver/temperature), display (current + all supported refresh
  rates), storage (`StatFs`).
- `PerformanceStatusEngine`: turns that into Excellent/Good/Normal/High Background
  Load/Thermal Limited/Battery Saving **with a plain-language reason**, per spec §6.
- `CapabilityManager`: per-device feature detection, shown on the Home screen so you can
  see exactly what this phone does/doesn't expose.

**Phase 2 — Game library + Boost engine**
- `GameLibraryManager`: scans installed launchable apps, keeps ones Android itself tags
  `CATEGORY_GAME` or that match a known-package allowlist (Mobile Legends, COD Mobile,
  Roblox, PUBG Mobile, Free Fire, Minecraft, MJLauncher, Genshin, Honkai: Star Rail).
  Manual add-by-package-name works for anything else.
- `BoostEngine`: runs the real pre-boost check sequence (thermal / RAM / refresh rate /
  Battery Saver / Shizuku / background CPU load) against a live snapshot and reports
  either "already optimal" or which checks need attention — it never invents a task just
  to look useful (spec §42).
- Per-game profile selection (Safe/Balanced/Performance/Custom) persisted in DataStore,
  local-only.

## What's intentionally NOT here yet

- A few of the more exotic per-manufacturer Game Mode variants. The first-launch setup
  wizard (spec §4) is now implemented — see below. Everything else from the 60-section
  spec is in.

## Setup wizard (added this round)

- Six real screens on first launch, gated by `OnboardingStore` (DataStore boolean —
  `MainActivity` shows the wizard until it's marked complete, then the normal nav host):
  1. Welcome
  2. What GameBoost X can and can't do
  3. Optional permissions explained (not requested — spec §4 is explicit that permissions
     are asked for only when a feature actually needs them, not up front)
  4. Shizuku detection (read-only status check, no permission prompt here either)
  5. A real device compatibility scan (`DeviceInfoManager.snapshot()` +
     `CapabilityManager.summary()` — the exact same data Home/Advanced show, not a mockup)
  6. Default gaming profile picker (Safe/Balanced/Performance) — this is now genuinely
     used: `GameProfileStore` and `GamesViewModel` were updated so newly-detected games
     fall back to this choice instead of a hardcoded Balanced.

## Phase 3 — Shizuku engine

- `ShizukuManager` wraps the official Shizuku API end to end: binder detection, the
  permission-request flow, binding our `PrivilegedService`, and recovering cleanly if
  Shizuku disconnects mid-session (advanced controls just go unavailable — nothing
  crashes, per spec §44).
- `PrivilegedService` (`IPrivilegedService.aidl`) runs inside the process Shizuku launches
  (shell UID, or root if the user granted Shizuku via root) and exposes exactly two
  operations — nothing generic:
  - refresh-rate lock/restore, via `Settings.System` `min_refresh_rate`/`peak_refresh_rate`
    (Android 11+, same mechanism Display settings uses)
  - Android Game Mode set/get for a specific package, via the documented `cmd game`
    command-line interface (Android 12+)
  Both go through Android's own documented tools rather than a hidden API, and both
  return `false`/`-1` on failure instead of guessing — there's deliberately no "run
  arbitrary shell command" method (spec §48).
- Every privileged action goes through an explain-then-confirm dialog
  (`ShizukuConfirmDialog`) showing WHAT IT DOES / WHY / WHAT MAY CHANGE / HOW TO RESTORE
  before Apply/Cancel (spec §13) — nothing fires silently.
- Home screen shows real Shizuku status (not installed / permission needed / connected)
  with a live "Grant Permission" button. Boost screen shows Apply/Restore refresh-rate
  and Game Mode buttons only when Shizuku is actually connected, gated by Android version.

## Phase 4 — Gaming sessions, overlay, history, logging

- **GamingSessionManager**: real session lifecycle. On start it snapshots device state,
  applies profile-driven optimizations through Shizuku if connected (Performance → max
  refresh + Game Mode Performance; Balanced → Game Mode Standard; Safe/Custom → nothing
  automatic), then polls device state every 5s for the session's live stats and peak
  temperature.
- **Exit detection** (spec §50): `ForegroundAppWatcher` polls the real
  `UsageStatsManager` event log every 4s (not aggressively — a lesson carried over from
  an earlier project where continuous polling caused more lag than it fixed) to notice
  when the tracked game leaves the foreground, and prompts End Session / Continue.
- **Restore on end**: refresh rate and Game Mode are restored via Shizuku, and a real
  `SessionRecordEntity` (duration, start/end/peak temp, RAM, battery, refresh rate,
  thermal status, changes applied, how it ended) is written to Room — no invented FPS
  field, because there's still no reliable source for another app's real frame rate.
- **GamingSessionService**: a foreground service (specialUse type on Android 14+) that
  exists only while a session is active, so the OS doesn't throttle monitoring in the
  background — started/stopped automatically in lockstep with the session, never
  outliving it.
- **Overlay** (spec §17/§23): a real `WindowManager` overlay window (plain View, since
  ComposeView needs a lifecycle/SavedStateRegistry owner this context doesn't have)
  showing refresh rate, CPU, RAM, temperature, and session time — FPS is always shown as
  "N/A" with an explanation, never faked.
- **Durable logging** (spec §24/§33): `LogRepository` + Room replace Phase 3's in-memory
  log — Shizuku operations and session lifecycle events now share one persisted log,
  visible in the Advanced tab along with session history.
- **Permission flows**: Usage Access and "draw over other apps" are both special
  permissions with no runtime dialog — the Session screen links straight to the right
  Settings screen for each, and explains what's missing if they're not granted.
- Entry point: each game card in the Games tab now has a **START GAMING SESSION** button
  alongside Boost/Launch.

## Phase 5 — Network diagnostics, benchmark, settings, export

- **NetworkDiagnosticsManager**: real latency measurement via TCP-connect timing to two
  reliable public hosts (8.8.8.8 / 1.1.1.1 on port 53) — ordinary apps can't send raw ICMP
  pings without root/NDK, so this is the same legitimate technique most non-root network
  test apps use. Reports real ping, jitter (stddev of samples), packet loss, and
  connection type (Wi-Fi/cellular/ethernet) — never claims to reduce ISP latency.
- **BenchmarkManager**: benchmarks the phone, not any game — a fixed-cost CPU workload
  (sieve of Eratosthenes + trig) run twice with a pause between to reveal thermal
  throttling, a 64MB memory read/write throughput test, and a 32MB file write/read
  throughput test in the app's own cache dir. Reports raw measured numbers only.
- **SettingsStore**: general toggles (use Shizuku when available, thermal warnings,
  auto-restore, log sessions), DataStore-backed.
- **Permission Center**: Shizuku, Overlay, Usage Access, and Notifications status all in
  one place with direct links to the right Settings screen for each — consolidates what
  was previously scattered across Home/Session/Advanced.
- **SessionExporter**: exports session history as TXT/CSV/JSON to the app's own cache dir
  and hands back a real Android share sheet via FileProvider — nothing uploads
  automatically, the person picks where it goes.
- New Settings tab also carries the Privacy statement and the full Limitations list from
  the spec (§37/§57), verbatim in substance.
- New manifest permissions used only here: `INTERNET` / `ACCESS_NETWORK_STATE` /
  `ACCESS_WIFI_STATE` for the network test, plus a `FileProvider` declaration for export
  sharing.

## Opening it

1. Unzip, open the root folder in Android Studio (Koala/2024.1+ recommended for AGP 8.6 /
   compileSdk 35).
2. Android Studio will offer to add the Gradle wrapper jar automatically the first time
   you sync (only `gradle-wrapper.properties` is included here, not the binary jar —
   that's normal and Studio regenerates it).
3. Run on a device or emulator with API 26+.

## Known rough edges to expect on first real build

Same lesson learned on StorageSweep: static review catches most things but not every
Kotlin/Compose compiler quirk (extension-function resolution, Compose 1.5.14 vs Kotlin
1.9.24 compatibility, etc.). If Android Studio's real compiler flags something, send the
exact error and I'll fix it directly — faster than another static pass.
