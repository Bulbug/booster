package com.gameboostx.app.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.view.Display
import android.view.WindowManager
import com.gameboostx.app.data.model.BatteryInfo
import com.gameboostx.app.data.model.CpuInfo
import com.gameboostx.app.data.model.DeviceSnapshot
import com.gameboostx.app.data.model.DisplayInfo
import com.gameboostx.app.data.model.MemoryInfo
import com.gameboostx.app.data.model.ThermalInfo
import com.gameboostx.app.data.model.ThermalStatus
import java.io.File
import java.io.RandomAccessFile

/**
 * Every read in this class is a real Android/Linux API call. If a device doesn't expose
 * something (no sysfs cpufreq node, no thermal status API pre-Android 10, etc.) the field
 * comes back null and the UI must say "unavailable" — never a guessed number (spec §11/§12/§26).
 */
class DeviceInfoManager(
    private val context: Context,
    private val capabilities: CapabilityManager,
) {
    // Kept between calls so CPU utilization is a real delta, not a blocking double-read.
    private var lastCpuSample: CpuSample? = null

    private data class CpuSample(val idle: Long, val total: Long, val atNanos: Long)

    fun snapshot(): DeviceSnapshot {
        val display = readDisplayInfo()
        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER ?: "Unknown",
            model = Build.MODEL ?: "Unknown",
            androidRelease = Build.VERSION.RELEASE ?: "Unknown",
            sdkInt = Build.VERSION.SDK_INT,
            cpu = readCpuInfo(),
            memory = readMemoryInfo(),
            thermal = readThermalInfo(),
            battery = readBatteryInfo(),
            display = display,
            storageAvailableBytes = readStorageAvailable(),
            storageTotalBytes = readStorageTotal(),
        )
    }

    // ---- CPU ----------------------------------------------------------------

    private fun readCpuInfo(): CpuInfo {
        val coreCount = Runtime.getRuntime().availableProcessors()
        val utilization = if (capabilities.supportsProcStat) readCpuUtilization() else null
        val (curFreq, maxFreq) = if (capabilities.supportsCpuFrequencySysfs) {
            readSysfsLong("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")?.times(1000) to
                readSysfsLong("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")?.times(1000)
        } else null to null
        return CpuInfo(
            coreCount = coreCount,
            utilizationFraction = utilization,
            currentFreqHz = curFreq,
            maxFreqHz = maxFreq,
        )
    }

    /** Reads /proc/stat's aggregate "cpu " line and returns a real utilization fraction as a delta since the last call. */
    private fun readCpuUtilization(): Float? {
        return try {
            RandomAccessFile("/proc/stat", "r").use { raf ->
                val line = raf.readLine() ?: return null
                val parts = line.trim().split(Regex("\\s+"))
                // cpu  user nice system idle iowait irq softirq steal guest guest_nice
                if (parts.size < 5 || parts[0] != "cpu") return null
                val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
                if (values.size < 4) return null
                val idle = values[3] + (values.getOrElse(4) { 0L }) // idle + iowait
                val total = values.sum()

                val prev = lastCpuSample
                val now = CpuSample(idle, total, System.nanoTime())
                lastCpuSample = now

                if (prev == null) return null // need a second sample for a delta
                val totalDelta = total - prev.total
                val idleDelta = idle - prev.idle
                if (totalDelta <= 0) return null
                (1f - (idleDelta.toFloat() / totalDelta.toFloat())).coerceIn(0f, 1f)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readSysfsLong(path: String): Long? = try {
        File(path).takeIf { it.canRead() }?.readText()?.trim()?.toLongOrNull()
    } catch (_: Exception) {
        null
    }

    // ---- Memory ---------------------------------------------------------------

    private fun readMemoryInfo(): MemoryInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return MemoryInfo(
            totalBytes = info.totalMem,
            availableBytes = info.availMem,
            usedBytes = info.totalMem - info.availMem,
            lowMemory = info.lowMemory,
            lowMemoryThresholdBytes = info.threshold,
        )
    }

    // ---- Thermal ----------------------------------------------------------------

    private fun readThermalInfo(): ThermalInfo {
        val batteryTemp = readBatteryTempCelsius()
        if (capabilities.supportsThermalStatus) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val status = when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.COOL
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.NORMAL
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.WARM
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.HOT
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.THERMAL_LIMIT
                else -> ThermalStatus.UNKNOWN
            }
            return ThermalInfo(status, batteryTemp, source = "PowerManager.getCurrentThermalStatus")
        }
        // Pre-Android 10: fall back to a battery-temperature heuristic instead of pretending we know real thermal state.
        val fallbackStatus = when {
            batteryTemp == null -> ThermalStatus.UNKNOWN
            batteryTemp < 35f -> ThermalStatus.COOL
            batteryTemp < 40f -> ThermalStatus.NORMAL
            batteryTemp < 45f -> ThermalStatus.WARM
            batteryTemp < 50f -> ThermalStatus.HOT
            else -> ThermalStatus.THERMAL_LIMIT
        }
        return ThermalInfo(fallbackStatus, batteryTemp, source = "battery temperature heuristic (no thermal API pre-Android 10)")
    }

    private fun readBatteryTempCelsius(): Float? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val raw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        return if (raw == Int.MIN_VALUE) null else raw / 10f
    }

    // ---- Battery ----------------------------------------------------------------

    private fun readBatteryInfo(): BatteryInfo {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return BatteryInfo(
            percent = percent,
            isCharging = isCharging,
            isBatterySaver = pm.isPowerSaveMode,
            tempCelsius = readBatteryTempCelsius(),
        )
    }

    // ---- Display ----------------------------------------------------------------

    @Suppress("DEPRECATION") // WindowManager#defaultDisplay is deprecated in API 30+, but minSdk 26 still needs it;
    // Display.getMode()/getSupportedModes() have no non-deprecated replacement until we bump minSdk to use context.display (API 30+).
    private fun readDisplayInfo(): DisplayInfo {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display: Display = wm.defaultDisplay
        val metrics = context.resources.displayMetrics
        val supported: List<Float> = if (capabilities.supportsHighRefreshRateQuery) {
            display.supportedModes.map { it.refreshRate }.distinct().sorted()
        } else {
            listOf(display.refreshRate)
        }
        return DisplayInfo(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            currentRefreshRateHz = display.refreshRate,
            supportedRefreshRatesHz = supported,
        )
    }

    // ---- Storage ----------------------------------------------------------------

    private fun readStorageAvailable(): Long = try {
        StatFs(Environment.getDataDirectory().path).let { it.availableBytes }
    } catch (_: Exception) {
        -1L
    }

    private fun readStorageTotal(): Long = try {
        StatFs(Environment.getDataDirectory().path).let { it.totalBytes }
    } catch (_: Exception) {
        -1L
    }
}
