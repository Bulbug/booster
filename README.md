# GameBoost X — Phase 1+2 scaffold

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

- **Shizuku integration** — Phase 3. The Boost screen already shows "Shizuku: not
  connected" honestly; nothing pretends to apply a system-level change yet (no refresh-
  rate lock, no Game Mode toggle). Adding the `dev.rikka.shizuku` dependency now so the
  build won't need a dependency-bump later, but it's unused so far.
- Gaming sessions, overlay, history/logging, network diagnostics, benchmark, settings
  screen, permission center — Phases 4/5 per the plan.

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
