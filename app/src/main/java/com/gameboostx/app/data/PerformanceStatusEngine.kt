package com.gameboostx.app.data

import com.gameboostx.app.data.model.DeviceSnapshot
import com.gameboostx.app.data.model.PerformanceStatus
import com.gameboostx.app.data.model.PerformanceStatusResult
import com.gameboostx.app.data.model.ThermalStatus

/**
 * Turns a DeviceSnapshot into one of the statuses from spec §6, with a plain-language reason.
 * Priority order matters: thermal and battery-saver concerns override a merely "busy" CPU,
 * because those are the two conditions Android itself will throttle performance for.
 */
object PerformanceStatusEngine {

    fun evaluate(snapshot: DeviceSnapshot): PerformanceStatusResult {
        val thermal = snapshot.thermal.status
        if (thermal == ThermalStatus.HOT || thermal == ThermalStatus.THERMAL_LIMIT) {
            return PerformanceStatusResult(
                PerformanceStatus.THERMAL_LIMITED,
                "Device temperature is elevated (${thermal.name.lowercase()}). Android may reduce performance to protect the hardware."
            )
        }

        if (snapshot.battery.isBatterySaver) {
            return PerformanceStatusResult(
                PerformanceStatus.BATTERY_SAVING,
                "Battery Saver is on, which typically limits background activity and CPU performance."
            )
        }

        if (snapshot.memory.lowMemory) {
            return PerformanceStatusResult(
                PerformanceStatus.HIGH_BACKGROUND_LOAD,
                "Available RAM is low (${snapshot.memory.availableBytes / (1024 * 1024)} MB free), which can force Android to kill and relaunch background apps more often."
            )
        }

        val cpuLoad = snapshot.cpu.utilizationFraction
        if (cpuLoad != null && cpuLoad > 0.75f) {
            return PerformanceStatusResult(
                PerformanceStatus.HIGH_BACKGROUND_LOAD,
                "Overall CPU load is high (${(cpuLoad * 100).toInt()}%) even before a game is factored in."
            )
        }

        if (thermal == ThermalStatus.WARM) {
            return PerformanceStatusResult(
                PerformanceStatus.NORMAL,
                "Device is warm but not yet in a throttling range. Worth watching during a long session."
            )
        }

        val goodMemory = snapshot.memory.availableBytes > snapshot.memory.totalBytes / 4
        val lowCpu = cpuLoad == null || cpuLoad < 0.35f
        val highRefreshAvailable = snapshot.display.supportedRefreshRatesHz.maxOrNull()?.let { it >= 90f } ?: false

        return if (goodMemory && lowCpu && thermal == ThermalStatus.COOL) {
            PerformanceStatusResult(
                PerformanceStatus.EXCELLENT,
                "Temperature, RAM headroom, and CPU load are all in a good range" +
                    if (highRefreshAvailable) " and a high refresh rate is available." else "."
            )
        } else {
            PerformanceStatusResult(
                PerformanceStatus.GOOD,
                "No thermal, memory, or battery-saver issues detected right now."
            )
        }
    }
}
