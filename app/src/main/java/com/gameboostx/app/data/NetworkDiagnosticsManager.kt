package com.gameboostx.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.sqrt

enum class ConnectionType { WIFI, CELLULAR, ETHERNET, OTHER, NONE }

data class PingSample(val roundTripMs: Long?) // null = that attempt failed (counts toward packet loss)

data class NetworkDiagnosticsResult(
    val connectionType: ConnectionType,
    val samples: List<PingSample>,
    val averageMs: Double?,
    val jitterMs: Double?,
    val packetLossPercent: Int,
) {
    val quality: String
        get() = when {
            packetLossPercent > 20 -> "Poor"
            averageMs == null -> "Unknown"
            averageMs < 60 && packetLossPercent == 0 -> "Excellent"
            averageMs < 120 -> "Good"
            else -> "Unstable"
        }
}

/**
 * Real latency measurement via TCP connect time to well-known, reliable hosts — the same
 * technique most non-root Android network-test apps use, since raw ICMP ping isn't available
 * to ordinary apps without native/root access. Nothing here claims to reduce ISP latency or
 * "boost" the connection (spec §19) — it only measures and reports.
 */
class NetworkDiagnosticsManager(private val context: Context) {

    suspend fun runTest(sampleCount: Int = 8): NetworkDiagnosticsResult = withContext(Dispatchers.IO) {
        val samples = mutableListOf<PingSample>()
        repeat(sampleCount) { i ->
            val host = TARGET_HOSTS[i % TARGET_HOSTS.size]
            samples += measureOnce(host, TARGET_PORT)
        }

        val successful = samples.mapNotNull { it.roundTripMs }
        val average = if (successful.isNotEmpty()) successful.average() else null
        val jitter = if (successful.size >= 2) stddev(successful) else null
        val lossPercent = (((samples.size - successful.size).toDouble() / samples.size) * 100).toInt()

        NetworkDiagnosticsResult(
            connectionType = currentConnectionType(),
            samples = samples,
            averageMs = average,
            jitterMs = jitter,
            packetLossPercent = lossPercent,
        )
    }

    private fun measureOnce(host: String, port: Int): PingSample {
        return try {
            val socket = Socket()
            val start = System.nanoTime()
            socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            socket.close()
            PingSample(elapsedMs)
        } catch (_: Exception) {
            PingSample(null)
        }
    }

    private fun stddev(values: List<Long>): Double {
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }

    private fun currentConnectionType(): ConnectionType {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return ConnectionType.NONE
        val network = cm.activeNetwork ?: return ConnectionType.NONE
        val caps = cm.getNetworkCapabilities(network) ?: return ConnectionType.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
            else -> ConnectionType.OTHER
        }
    }

    companion object {
        // Reliable, high-uptime hosts on a nearly-always-open port — not tied to any one game's servers.
        private val TARGET_HOSTS = listOf("8.8.8.8", "1.1.1.1")
        private const val TARGET_PORT = 53 // DNS — open on both targets, doesn't require an HTTP round trip
        private const val TIMEOUT_MS = 2_000
    }
}
