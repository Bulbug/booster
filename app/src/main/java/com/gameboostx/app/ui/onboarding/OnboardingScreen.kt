package com.gameboostx.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gameboostx.app.data.model.ProfileType
import com.gameboostx.app.ui.components.SectionHeader

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                when (state.step) {
                    0 -> WelcomeStep()
                    1 -> WhatItDoesStep()
                    2 -> PermissionsExplainedStep()
                    3 -> ShizukuDetectStep(state.shizukuBinderAvailable)
                    4 -> CompatibilityScanStep(state)
                    5 -> DefaultProfileStep(state.selectedDefaultProfile, viewModel::selectDefaultProfile)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (state.step > 0) {
                TextButton(onClick = { viewModel.back() }) { Text("Back") }
            } else {
                Text("")
            }

            if (state.step < ONBOARDING_STEP_COUNT - 1) {
                Button(onClick = { viewModel.next() }) { Text("Next") }
            } else {
                Button(onClick = { viewModel.finish(onFinished) }) { Text("Get Started") }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Text("GAMEBOOST X", style = MaterialTheme.typography.headlineLarge)
    Text(
        "Optimize your Android gaming environment safely.",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        "This is a device and system optimizer, not a game mod. It never touches game files, " +
            "never bypasses anti-cheat, and never fakes a performance number.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Composable
private fun WhatItDoesStep() {
    SectionHeader("WHAT GAMEBOOST X CAN DO")
    listOf(
        "Show real CPU, RAM, temperature, battery, and refresh-rate readings",
        "Run a real check sequence before you launch a game and report what it finds",
        "Optionally use Shizuku for a refresh-rate lock and Android's own Game Mode API",
        "Track a gaming session's device stats and restore anything it changed when you're done",
        "Run a real network and phone benchmark — never a simulated one",
    ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }

    SectionHeader("WHAT IT WON'T DO")
    listOf(
        "Modify game files, inject code, or bypass anti-cheat/DRM",
        "Automate gameplay or unlock paid content",
        "Guarantee a specific FPS or guarantee zero lag",
        "Require root, or request a permission before a feature actually needs it",
    ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
}

@Composable
private fun PermissionsExplainedStep() {
    SectionHeader("OPTIONAL PERMISSIONS")
    Text(
        "None of these are requested now — each one is asked for only when you actually use the " +
            "feature that needs it, and the app works without any of them.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    listOf(
        "Shizuku" to "Lets the Boost screen lock refresh rate and change Game Mode. Skip it and those two buttons just stay hidden.",
        "Draw over other apps" to "Lets a gaming session show a small stats overlay. Optional.",
        "Usage Access" to "Lets a gaming session notice when you've exited the game automatically. You can always end a session manually instead.",
        "Notifications" to "Shows the ongoing-session notification Android requires for background monitoring.",
    ).forEach { (name, desc) ->
        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ShizukuDetectStep(binderAvailable: Boolean) {
    SectionHeader("SHIZUKU")
    Text(
        if (binderAvailable) "Shizuku is installed and running on this device." else "Shizuku isn't running on this device right now.",
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        "That's fine either way — GameBoost X's core monitoring, boost checks, and gaming sessions " +
            "all work without it. You can install or start Shizuku any time later from Settings.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun CompatibilityScanStep(state: OnboardingUiState) {
    SectionHeader("DEVICE COMPATIBILITY SCAN")
    val snap = state.deviceSnapshot
    if (state.scanning || snap == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
            Text("Scanning this device...")
        }
        return
    }
    Text("${snap.manufacturer} ${snap.model} · Android ${snap.androidRelease}", style = MaterialTheme.typography.bodyMedium)
    Text(
        "Max refresh rate: ${snap.display.supportedRefreshRatesHz.maxOrNull()?.toInt() ?: snap.display.currentRefreshRateHz.toInt()}Hz",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        "${state.capabilities.count { it.value }} of ${state.capabilities.size} optional capabilities supported on this device — see Advanced for the full list.",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun DefaultProfileStep(selected: ProfileType, onSelect: (ProfileType) -> Unit) {
    SectionHeader("DEFAULT GAMING PROFILE")
    Text(
        "Newly detected games start on this profile. You can change it per-game any time in the Games tab.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    listOf(ProfileType.SAFE, ProfileType.BALANCED, ProfileType.PERFORMANCE).forEach { profile ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected == profile, onClick = { onSelect(profile) })
            Column(Modifier.padding(start = 8.dp)) {
                Text(profile.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    when (profile) {
                        ProfileType.SAFE -> "Minimal changes, nothing automatic."
                        ProfileType.BALANCED -> "Moderate — sets Game Mode to Standard when Shizuku is available."
                        else -> "Prioritizes performance — max refresh rate + Game Mode Performance when Shizuku is available."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
