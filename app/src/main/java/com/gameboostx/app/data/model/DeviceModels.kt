package com.gameboostx.app.data.model

/**
 * Every field here is either a real value read from an Android API, or null/Unavailable
 * when the platform doesn't expose it on this device. Nothing in this file is ever
 * fabricated by the app itself — see spec §26 "No fake features" / §41 anti-placebo rule.
 */

data class CpuInfo(
    val coreCount: Int,
    /** 0f..1f fraction, derived from /proc/stat deltas. Null if unreadable on this device/Android version. */
    val utilizationFraction: Float?,
    /** Hz, from /sys/devices/system/cpu/cpu0/cpufreq. Null if the sysfs node isn't exposed. */
    val currentFreqHz: Long?,
    val maxFreqHz: Long?,
)

data class MemoryInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long,
    val lowMemory: Boolean,
    val lowMemoryThresholdBytes: Long,
)

enum class ThermalStatus { COOL, NORMAL, WARM, HOT, THERMAL_LIMIT, UNKNOWN }

data class ThermalInfo(
    val status: ThermalStatus,
    /** Celsius, from the battery sticky broadcast (the one temperature every device exposes). Divide raw/10. */
    val batteryTempCelsius: Float?,
    val source: String,
)

data class BatteryInfo(
    val percent: Int,
    val isCharging: Boolean,
    val isBatterySaver: Boolean,
    val tempCelsius: Float?,
)

data class DisplayInfo(
    val widthPx: Int,
    val heightPx: Int,
    val currentRefreshRateHz: Float,
    val supportedRefreshRatesHz: List<Float>,
)

enum class PerformanceStatus(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    NORMAL("Normal"),
    HIGH_BACKGROUND_LOAD("High Background Load"),
    THERMAL_LIMITED("Thermal Limited"),
    BATTERY_SAVING("Battery Saving"),
}

data class PerformanceStatusResult(
    val status: PerformanceStatus,
    val reason: String,
)

data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val cpu: CpuInfo,
    val memory: MemoryInfo,
    val thermal: ThermalInfo,
    val battery: BatteryInfo,
    val display: DisplayInfo,
    val storageAvailableBytes: Long,
    val storageTotalBytes: Long,
)
