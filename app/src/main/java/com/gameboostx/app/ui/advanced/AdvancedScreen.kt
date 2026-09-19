package com.gameboostx.app.ui.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gameboostx.app.ui.components.SectionHeader

@Composable
fun AdvancedScreen(viewModel: AdvancedViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    val shizuku = state.shizuku

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { Text("ADVANCED", style = MaterialTheme.typography.headlineMedium) }

        item {
            SectionHeader("SHIZUKU STATUS")
            Text("Binder available: ${if (shizuku.binderAvailable) "yes" else "no"}", style = MaterialTheme.typography.bodyMedium)
            Text("Permission granted: ${if (shizuku.permissionGranted) "yes" else "no"}", style = MaterialTheme.typography.bodyMedium)
            Text("Privileged service connected: ${if (shizuku.serviceBound) "yes" else "no"}", style = MaterialTheme.typography.bodyMedium)
            if (shizuku.binderAvailable && !shizuku.permissionGranted) {
                TextButton(onClick = { viewModel.requestPermission() }) { Text("Grant Permission") }
            }
        }

        item { SectionHeader("SUPPORTED OPERATIONS") }
        item {
            Text(
                if (shizuku.serviceBound) "Refresh rate lock/restore, Game Mode (Standard/Performance) — Battery mode available where the device supports it."
                else "None available until Shizuku connects.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Only shown when a real vendor app was actually detected — never a guessed or assumed mode (spec §11/§26).
        state.oemGameMode?.let { oem ->
            item { SectionHeader("VENDOR GAME MODE") }
            item {
                val context = androidx.compose.ui.platform.LocalContext.current
                Column {
                    Text("${oem.vendorLabel} — ${oem.appLabel}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Detected on this device. GameBoost X can't control its settings directly — there's no public API for " +
                            "vendor game-mode apps, and they change across ROM versions — but it can open the vendor's own app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        viewModel.oemLaunchIntent(oem.packageName)?.let { context.startActivity(it) }
                    }) { Text("Open ${oem.appLabel}") }
                }
            }
        }

        item { SectionHeader("DEVICE CAPABILITIES") }
        items(state.capabilities.entries.toList()) { (label, supported) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (supported) "Supported" else "Unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (supported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionHeader("OPTIMIZATION LOG") }
        if (state.log.isEmpty()) {
            item { Text("No Shizuku operations yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(state.log) { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
            }
        }

        item { SectionHeader("SESSION HISTORY") }
        if (state.recentSessions.isEmpty()) {
            item { Text("No gaming sessions recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(state.recentSessions.take(20)) { session ->
                SessionHistoryRow(session)
            }
        }

        item {
            Text(
                "The optimization log keeps the most recent 300 entries on-device; nothing is uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun SessionHistoryRow(session: com.gameboostx.app.data.db.SessionRecordEntity) {
    val durationMin = ((session.endedAtMillis - session.startedAtMillis) / 60000).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("${session.displayName} · ${session.profile}", style = MaterialTheme.typography.titleMedium)
        Text(
            "${durationMin} min · ${session.startTempCelsius?.let { "%.0f".format(it) } ?: "?"}°C → " +
                "${session.endTempCelsius?.let { "%.0f".format(it) } ?: "?"}°C" +
                (session.peakTempCelsius?.let { " (peak %.0f°C)".format(it) } ?: "") +
                " · ${session.startBatteryPercent}% → ${session.endBatteryPercent}% · " +
                "${session.refreshRateHz.toInt()}Hz · ended: ${session.endedReason}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
