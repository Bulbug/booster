package com.gameboostx.app.data

import android.content.Context
import android.os.Build
import android.os.PowerManager
import java.io.File

/**
 * Centralizes "can this device/Android version actually do X" checks so the rest of the app
 * never has to guess or assume a manufacturer API exists (spec §3, §43).
 */
class CapabilityManager(private val context: Context) {

    val supportsThermalStatus: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    val supportsHighRefreshRateQuery: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    val supportsGameMode: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val supportsBatteryTemperature: Boolean = true // sticky broadcast extra, available on all real devices

    val supportsCpuFrequencySysfs: Boolean by lazy {
        File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq").canRead()
    }

    val supportsProcStat: Boolean by lazy {
        File("/proc/stat").canRead()
    }

    val supportsLowMemoryDetection: Boolean = true // ActivityManager.MemoryInfo, all API levels

    fun powerManager(): PowerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    /** Human-readable summary for the Advanced/Debug screens (spec §48/§53). */
    fun summary(): Map<String, Boolean> = mapOf(
        "Thermal status API" to supportsThermalStatus,
        "High refresh rate query" to supportsHighRefreshRateQuery,
        "Android Game Mode API" to supportsGameMode,
        "Battery temperature" to supportsBatteryTemperature,
        "CPU frequency (sysfs)" to supportsCpuFrequencySysfs,
        "CPU utilization (/proc/stat)" to supportsProcStat,
        "Low memory detection" to supportsLowMemoryDetection,
    )
}
