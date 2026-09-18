package com.gameboostx.app.data

import com.gameboostx.app.data.model.BoostCheckItem
import com.gameboostx.app.data.model.BoostPlanResult
import com.gameboostx.app.data.model.DeviceSnapshot
import com.gameboostx.app.data.model.ThermalStatus

/**
 * Runs the real check sequence from spec §4/§9 against a live DeviceSnapshot and produces an
 * honest result. Phase 1+2 has no Shizuku engine yet, so this never claims to change anything —
 * it reports what it found and, once Phase 3 lands, the same checks will gate which changes
 * ShizukuManager is allowed to apply. "Your device is already in a good state" is a valid,
 * expected result (spec §42) — this must never invent work just to look useful.
 */
class BoostEngine {

    fun analyze(snapshot: DeviceSnapshot, shizukuConnected: Boolean): BoostPlanResult {
        val checks = mutableListOf<BoostCheckItem>()

        val thermalOk = snapshot.thermal.status == ThermalStatus.COOL || snapshot.thermal.status == ThermalStatus.NORMAL
        checks += BoostCheckItem(
            "Thermal state",
            thermalOk,
            if (thermalOk) "Temperature is in a normal range." else "Device is ${snapshot.thermal.status.name.lowercase()} — pushing performance now could trigger throttling."
        )

        val memOk = !snapshot.memory.lowMemory
        checks += BoostCheckItem(
            "Available RAM",
            memOk,
            if (memOk) "${snapshot.memory.availableBytes / (1024 * 1024)} MB free, no memory pressure." else "Android is reporting low memory — background apps may already be getting killed."
        )

        val refreshRates = snapshot.display.supportedRefreshRatesHz
        val maxRefresh = refreshRates.maxOrNull() ?: snapshot.display.currentRefreshRateHz
        val atMaxRefresh = snapshot.display.currentRefreshRateHz >= maxRefresh - 0.5f
        checks += BoostCheckItem(
            "Refresh rate",
            atMaxRefresh,
            if (atMaxRefresh) "Already running at the highest supported rate (${maxRefresh.toInt()} Hz)."
            else "Currently ${snapshot.display.currentRefreshRateHz.toInt()} Hz; up to ${maxRefresh.toInt()} Hz is supported. " +
                "Switching this automatically needs Shizuku (not yet connected) — you can change it manually in Display settings."
        )

        val batterySaverOk = !snapshot.battery.isBatterySaver
        checks += BoostCheckItem(
            "Battery Saver",
            batterySaverOk,
            if (batterySaverOk) "Off." else "Battery Saver is on, which limits performance while it's active."
        )

        checks += BoostCheckItem(
            "Shizuku connection",
            shizukuConnected,
            if (shizukuConnected) "Connected — advanced optimizations are available." else "Not connected. Basic monitoring and manual recommendations still work without it."
        )

        val cpuLoad = snapshot.cpu.utilizationFraction
        val cpuOk = cpuLoad == null || cpuLoad < 0.6f
        checks += BoostCheckItem(
            "Background CPU load",
            cpuOk,
            if (cpuLoad == null) "Couldn't be measured on this device."
            else if (cpuOk) "${(cpuLoad * 100).toInt()}% — normal." else "${(cpuLoad * 100).toInt()}% even before the game starts — something in the background is busy."
        )

        val allPassed = checks.filter { it.label != "Shizuku connection" }.all { it.passed }
        val summary = if (allPassed) {
            "Your device is already in a good state for gaming. No changes are needed right now."
        } else {
            val issues = checks.filter { !it.passed && it.label != "Shizuku connection" }.joinToString(", ") { it.label }
            "Found $issues worth addressing before you launch."
        }

        return BoostPlanResult(checks = checks, alreadyOptimal = allPassed, summary = summary)
    }
}
