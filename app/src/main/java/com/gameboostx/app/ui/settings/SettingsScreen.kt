package com.gameboostx.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gameboostx.app.data.ExportFormat
import com.gameboostx.app.ui.components.SectionHeader
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { Text("SETTINGS", style = MaterialTheme.typography.headlineMedium) }

        // ---- Permission Center (spec §35) ----
        item { SectionHeader("PERMISSION CENTER") }
        item {
            val shizuku = state.shizuku
            PermissionRow(
                name = "Shizuku",
                status = when {
                    shizuku.serviceBound -> "Granted"
                    shizuku.binderAvailable -> "Not granted"
                    else -> "Not installed / not running"
                },
                explanation = "Enables refresh-rate lock and Game Mode changes. Optional — the app works without it.",
                actionLabel = if (shizuku.binderAvailable && !shizuku.permissionGranted) "Grant" else null,
                onAction = { viewModel.requestShizukuPermission() },
            )
        }
        item {
            PermissionRow(
                name = "Overlay (draw over other apps)",
                status = if (viewModel.overlayGranted(context)) "Granted" else "Not granted",
                explanation = "Lets a gaming session show a small stats overlay. Optional.",
                actionLabel = if (!viewModel.overlayGranted(context)) "Open Settings" else null,
                onAction = { context.startActivity(com.gameboostx.app.data.OverlayPermissionHelper.settingsIntent(context)) },
            )
        }
        item {
            PermissionRow(
                name = "Usage Access",
                status = if (viewModel.usageAccessGranted(context)) "Granted" else "Not granted",
                explanation = "Lets a gaming session notice when you've exited the game. Optional — you can always end a session manually.",
                actionLabel = if (!viewModel.usageAccessGranted(context)) "Open Settings" else null,
                onAction = { context.startActivity(com.gameboostx.app.data.UsageAccessHelper.settingsIntent()) },
            )
        }
        item {
            val notifGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            PermissionRow(
                name = "Notifications",
                status = if (notifGranted) "Granted" else "Not granted",
                explanation = "Shows the ongoing gaming-session notification required by Android for background monitoring.",
                actionLabel = null,
                onAction = {},
            )
        }

        // ---- General toggles (spec §36) ----
        item { SectionHeader("GENERAL") }
        item {
            ToggleRow("Use Shizuku when available", state.appSettings.useShizukuWhenAvailable, viewModel::setUseShizuku)
            ToggleRow("Show thermal warnings", state.appSettings.showThermalWarnings, viewModel::setThermalWarnings)
            ToggleRow("Restore settings automatically", state.appSettings.restoreSettingsAutomatically, viewModel::setAutoRestore)
            ToggleRow("Log gaming sessions", state.appSettings.logGamingSessions, viewModel::setLogSessions)
        }

        // ---- Network diagnostics (spec §26-27) ----
        item { SectionHeader("NETWORK DIAGNOSTICS") }
        item {
            Button(onClick = { viewModel.runNetworkTest() }, enabled = !state.networkTestRunning) {
                Text(if (state.networkTestRunning) "Testing…" else "Run Network Test")
            }
            state.networkResult?.let { result ->
                Column(Modifier.padding(top = 8.dp)) {
                    Text("Connection: ${result.connectionType}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Ping: ${result.averageMs?.let { "%.0f ms".format(it) } ?: "unavailable"}  ·  " +
                            "Jitter: ${result.jitterMs?.let { "%.0f ms".format(it) } ?: "unavailable"}  ·  " +
                            "Packet loss: ${result.packetLossPercent}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Quality: ${result.quality}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "High ping means network delay; jitter means ping instability; packet loss means data is being lost in transit. " +
                            "GameBoost X can't reduce ISP latency — try a stable Wi-Fi connection, move closer to the router, or avoid downloads while gaming.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        // ---- Benchmark (spec §18/§28) ----
        item { SectionHeader("PERFORMANCE BENCHMARK") }
        item {
            Button(onClick = { viewModel.runBenchmark() }, enabled = !state.benchmarkRunning) {
                Text(if (state.benchmarkRunning) "Running…" else "Run Benchmark")
            }
            state.benchmarkResult?.let { r ->
                Column(Modifier.padding(top = 8.dp)) {
                    Text("CPU: ${r.cpuFirstPassMs} ms (2nd pass ${r.cpuSecondPassMs} ms) — ${r.sustainedPerformanceNote}", style = MaterialTheme.typography.bodyMedium)
                    Text("Memory throughput: %.0f MB/s".format(r.memoryThroughputMbPerSec), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Storage: write ${r.storageWriteMbPerSec?.let { "%.0f MB/s".format(it) } ?: "unavailable"}, " +
                            "read ${r.storageReadMbPerSec?.let { "%.0f MB/s".format(it) } ?: "unavailable"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Temperature: ${r.startTempCelsius ?: "?"}°C → ${r.endTempCelsius ?: "?"}°C (${r.startThermalStatus} → ${r.endThermalStatus})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "This measures the phone, not any specific game — it isn't a predicted FPS.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        // ---- Export (spec §52) ----
        item { SectionHeader("EXPORT SESSION HISTORY") }
        item {
            Text("${state.sessionCount} session(s) recorded.", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportButton(viewModel, ExportFormat.TXT)
                ExportButton(viewModel, ExportFormat.CSV)
                ExportButton(viewModel, ExportFormat.JSON)
            }
        }

        // ---- Privacy + limitations (spec §37/§57) ----
        item { SectionHeader("PRIVACY") }
        item {
            Text(
                "GameBoost X processes everything on-device. It doesn't collect passwords, chat messages, personal files, screenshots, contacts, or location, " +
                    "and doesn't upload session data or logs unless you explicitly export and share them yourself.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        item { SectionHeader("LIMITATIONS") }
        item {
            Text(
                "GameBoost X cannot guarantee a specific FPS, cannot guarantee zero lag, cannot make hardware perform beyond its " +
                    "physical capabilities, cannot override a game's built-in FPS limit, cannot control ISP routing or guarantee lower " +
                    "ping, does not modify game files, and does not bypass anti-cheat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExportButton(viewModel: SettingsViewModel, format: ExportFormat) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    OutlinedButton(onClick = {
        scope.launch {
            val intent = viewModel.buildExportIntent(format)
            context.startActivity(android.content.Intent.createChooser(intent, "Share session history"))
        }
    }) { Text(format.name) }
}

@Composable
private fun PermissionRow(
    name: String,
    status: String,
    explanation: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
