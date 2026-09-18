package com.gameboostx.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gameboostx.app.data.model.DeviceSnapshot
import com.gameboostx.app.data.model.ThermalStatus
import com.gameboostx.app.ui.components.SectionHeader
import com.gameboostx.app.ui.components.StatCard
import com.gameboostx.app.ui.components.StatusColorFor

@Composable
fun HomeScreen(viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.startPollingIfNeeded() }

    if (state.loading || state.snapshot == null) {
        Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val snapshot = state.snapshot!!
    val statusResult = state.statusResult!!

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text("GAMEBOOST X", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = "${snapshot.manufacturer} ${snapshot.model} · Android ${snapshot.androidRelease}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            SectionHeader("PERFORMANCE STATUS")
            Text(
                text = statusResult.status.label.uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = StatusColorFor(statusResult.status),
            )
            Text(
                text = statusResult.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            SectionHeader("DEVICE")
            StatGrid(snapshot)
        }

        item {
            SectionHeader("SHIZUKU")
            Text(
                text = if (state.shizukuConnected) "CONNECTED ✓" else "NOT CONNECTED — basic monitoring still works. Advanced controls arrive in a later build.",
                style = MaterialTheme.typography.bodyLarge,
                color = if (state.shizukuConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            SectionHeader("DEVICE CAPABILITIES")
        }
        items(viewModel.capabilitySummary().entries.toList()) { (label, supported) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (supported) "Supported" else "Unavailable on this device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (supported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatGrid(snapshot: DeviceSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                "CPU",
                snapshot.cpu.utilizationFraction?.let { "${(it * 100).toInt()}%" } ?: "Unavailable",
                caption = "${snapshot.cpu.coreCount} cores" + (snapshot.cpu.maxFreqHz?.let { " · max ${it / 1_000_000} MHz" } ?: ""),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                "RAM",
                "${bytesToGb(snapshot.memory.usedBytes)} / ${bytesToGb(snapshot.memory.totalBytes)} GB",
                caption = if (snapshot.memory.lowMemory) "Low memory" else "${bytesToGb(snapshot.memory.availableBytes)} GB free",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                "Temperature",
                snapshot.thermal.batteryTempCelsius?.let { "${"%.1f".format(it)}°C" } ?: "Unavailable",
                caption = snapshot.thermal.status.readable(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                "Battery",
                "${snapshot.battery.percent}%",
                caption = when {
                    snapshot.battery.isCharging -> "Charging"
                    snapshot.battery.isBatterySaver -> "Battery Saver on"
                    else -> "Not charging"
                },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                "Refresh Rate",
                "${snapshot.display.currentRefreshRateHz.toInt()} Hz",
                caption = "Supports " + snapshot.display.supportedRefreshRatesHz.joinToString(" / ") { "${it.toInt()}Hz" },
                modifier = Modifier.weight(1f),
            )
            StatCard(
                "Storage",
                "${bytesToGb(snapshot.storageAvailableBytes)} GB free",
                caption = "of ${bytesToGb(snapshot.storageTotalBytes)} GB",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun bytesToGb(bytes: Long): String =
    if (bytes < 0) "?" else "%.1f".format(bytes / (1024.0 * 1024.0 * 1024.0))

private fun ThermalStatus.readable(): String = when (this) {
    ThermalStatus.COOL -> "Cool"
    ThermalStatus.NORMAL -> "Normal"
    ThermalStatus.WARM -> "Warm"
    ThermalStatus.HOT -> "Hot"
    ThermalStatus.THERMAL_LIMIT -> "Thermal limit"
    ThermalStatus.UNKNOWN -> "Unknown"
}
