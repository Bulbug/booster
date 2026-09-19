package com.gameboostx.app.ui.session

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gameboostx.app.data.OverlayPermissionHelper
import com.gameboostx.app.data.UsageAccessHelper
import com.gameboostx.app.data.model.ProfileType
import com.gameboostx.app.ui.components.SectionHeader
import com.gameboostx.app.ui.components.StatCard

@Composable
fun SessionScreen(
    viewModel: SessionViewModel,
    packageName: String,
    displayName: String,
    profile: ProfileType,
    onEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var overlayOn by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // Start once when this screen first appears for this game.
    remember(packageName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.start(packageName, displayName, profile)
        true
    }

    if (state.exitDetected) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Gaming session appears to have ended") },
            text = { Text("$displayName is no longer in the foreground.") },
            confirmButton = {
                TextButton(onClick = { viewModel.endSession("auto_detected_exit"); onEnded() }) { Text("End Session") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissExitDetected() }) { Text("Continue") }
            },
        )
    }

    if (!state.active) {
        Column(modifier.fillMaxSize().padding(24.dp)) {
            Text("Session ended.", style = MaterialTheme.typography.titleLarge)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("GAMING SESSION", style = MaterialTheme.typography.headlineMedium)
            Text(displayName, style = MaterialTheme.typography.titleLarge)
            val minutes = state.elapsedSeconds / 60
            val seconds = state.elapsedSeconds % 60
            Text(
                "%02d:%02d".format(minutes, seconds),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        item { SectionHeader("LIVE STATS") }
        item {
            val snap = state.latestSnapshot
            if (snap == null) {
                Text("Gathering first reading…", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(
                            "Temperature",
                            snap.thermal.batteryTempCelsius?.let { "%.1f°C".format(it) } ?: "Unavailable",
                            caption = state.peakTempCelsius?.let { "Peak %.1f°C".format(it) },
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            "RAM",
                            "%.1f GB used".format(snap.memory.usedBytes / (1024.0 * 1024.0 * 1024.0)),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Refresh Rate", "${snap.display.currentRefreshRateHz.toInt()} Hz", modifier = Modifier.weight(1f))
                        StatCard("Battery", "${snap.battery.percent}%", modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Text(
                "FPS: N/A — no reliable way to read another app's real frame rate on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item { SectionHeader("OVERLAY") }
        item {
            if (!OverlayPermissionHelper.isGranted(context)) {
                Column {
                    Text("Overlay permission not granted.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { context.startActivity(OverlayPermissionHelper.settingsIntent(context)) }) {
                        Text("Open Overlay Settings")
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (overlayOn) "Overlay is on" else "Overlay is off", modifier = Modifier.padding(end = 12.dp))
                    OutlinedButton(onClick = { overlayOn = viewModel.toggleOverlay(context) }) {
                        Text(if (overlayOn) "Turn Off" else "Turn On")
                    }
                }
            }
        }

        if (!UsageAccessHelper.isGranted(context)) {
            item { SectionHeader("EXIT DETECTION") }
            item {
                Column {
                    Text(
                        "Usage Access isn't granted, so GameBoost X can't automatically notice when you exit the game.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { context.startActivity(UsageAccessHelper.settingsIntent()) }) {
                        Text("Open Usage Access Settings")
                    }
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.endSession("manual"); onEnded() },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) { Text("END SESSION") }
        }
    }
}
