package com.gameboostx.app.data

import android.content.Context
import com.gameboostx.app.data.model.ThermalStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

data class BenchmarkResult(
    val cpuFirstPassMs: Long,
    val cpuSecondPassMs: Long, // run again after a short pause — a large gap vs. the first pass suggests thermal throttling
    val memoryThroughputMbPerSec: Double,
    val storageWriteMbPerSec: Double?,
    val storageReadMbPerSec: Double?,
    val startTempCelsius: Float?,
    val endTempCelsius: Float?,
    val startThermalStatus: ThermalStatus,
    val endThermalStatus: ThermalStatus,
    val durationMs: Long,
) {
    val sustainedPerformanceNote: String
        get() {
            if (cpuFirstPassMs <= 0) return "Not enough data."
            val ratio = cpuSecondPassMs.toDouble() / cpuFirstPassMs.toDouble()
            return when {
                ratio > 1.25 -> "Noticeably slower on the second pass — possible thermal throttling."
                ratio > 1.08 -> "Slightly slower on the second pass."
                else -> "Consistent between passes."
            }
        }
}

/**
 * Benchmarks the PHONE, not any game (spec §18) — a fixed, deterministic amount of real work is
 * timed, not simulated. There is no "score" invented beyond the raw measured numbers; the app
 * never claims this predicts a specific game's FPS.
 */
class BenchmarkManager(
    private val context: Context,
    private val deviceInfoManager: DeviceInfoManager,
) {
    suspend fun run(): BenchmarkResult = withContext(Dispatchers.Default) {
        val startSnapshot = deviceInfoManager.snapshot()
        val overallStart = System.currentTimeMillis()

        val cpu1 = timeCpuWork()
        val memThroughput = timeMemoryWork()
        val (writeMb, readMb) = timeStorageWork()
        kotlinx.coroutines.delay(1_500L) // brief pause, mirrors spec's "sustained performance" pass
        val cpu2 = timeCpuWork()

        val endSnapshot = deviceInfoManager.snapshot()

        BenchmarkResult(
            cpuFirstPassMs = cpu1,
            cpuSecondPassMs = cpu2,
            memoryThroughputMbPerSec = memThroughput,
            storageWriteMbPerSec = writeMb,
            storageReadMbPerSec = readMb,
            startTempCelsius = startSnapshot.thermal.batteryTempCelsius,
            endTempCelsius = endSnapshot.thermal.batteryTempCelsius,
            startThermalStatus = startSnapshot.thermal.status,
            endThermalStatus = endSnapshot.thermal.status,
            durationMs = System.currentTimeMillis() - overallStart,
        )
    }

    /** Fixed, deterministic integer/float workload (sieve + trig) — same cost every run, so timing differences reflect the device, not randomness. */
    private fun timeCpuWork(): Long {
        val start = System.currentTimeMillis()
        val limit = 2_000_000
        val sieve = BooleanArray(limit + 1)
        var count = 0
        for (i in 2..limit) {
            if (!sieve[i]) {
                count++
                var j = i.toLong() * i
                while (j <= limit) {
                    sieve[j.toInt()] = true
                    j += i
                }
            }
        }
        var acc = 0.0
        for (i in 0 until 2_000_000) acc += Math.sin(i.toDouble()) * Math.cos(i.toDouble())
        if (count < 0 || acc.isNaN()) throw IllegalStateException("unreachable")
        return System.currentTimeMillis() - start
    }

    /** Allocates and fully read/writes a fixed-size buffer; throughput in MB/s. */
    private fun timeMemoryWork(): Double {
        val sizeBytes = 64 * 1024 * 1024
        val buffer = ByteArray(sizeBytes)
        val start = System.currentTimeMillis()
        val rnd = Random(42)
        for (i in buffer.indices step 4096) buffer[i] = (rnd.nextInt(256) - 128).toByte()
        var checksum = 0L
        for (b in buffer) checksum += b
        val elapsedSec = (System.currentTimeMillis() - start).coerceAtLeast(1) / 1000.0
        if (checksum == Long.MIN_VALUE) throw IllegalStateException("unreachable")
        return (sizeBytes / (1024.0 * 1024.0)) / elapsedSec
    }

    /** Writes then reads a fixed-size temp file in the app's own cache dir; throughput in MB/s. Returns nulls if storage isn't writable. */
    private fun timeStorageWork(): Pair<Double?, Double?> {
        val sizeBytes = 32 * 1024 * 1024
        val data = ByteArray(sizeBytes) { (it % 256).toByte() }
        val file = File(context.cacheDir, "benchmark_tmp.bin")
        return try {
            val writeStart = System.currentTimeMillis()
            file.outputStream().use { out ->
                out.write(data)
                out.flush()
            }
            val writeSec = (System.currentTimeMillis() - writeStart).coerceAtLeast(1) / 1000.0
            val writeMb = (sizeBytes / (1024.0 * 1024.0)) / writeSec

            val readStart = System.currentTimeMillis()
            var readBytes = 0
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    readBytes += n
                }
            }
            val readSec = (System.currentTimeMillis() - readStart).coerceAtLeast(1) / 1000.0
            val readMb = (readBytes / (1024.0 * 1024.0)) / readSec

            file.delete()
            writeMb to readMb
        } catch (_: Exception) {
            file.delete()
            null to null
        }
    }
}
