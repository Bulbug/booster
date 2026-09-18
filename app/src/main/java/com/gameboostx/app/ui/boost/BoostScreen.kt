package com.gameboostx.app.ui.boost

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gameboostx.app.ui.components.BoostCheckRow
import com.gameboostx.app.ui.theme.SurfaceElevated

@Composable
fun BoostScreen(
    viewModel: BoostViewModel,
    onLaunchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    if (state.running || state.result == null) {
        Column(
            modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Text("Analyzing device...", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    val result = state.result!!

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { Text("BOOST ANALYSIS", style = MaterialTheme.typography.headlineMedium) }

        items(result.checks) { check -> BoostCheckRow(check) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (result.alreadyOptimal) "Result: already optimal" else "Result: needs attention",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(result.summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        item {
            Button(onClick = onLaunchClick, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("LAUNCH GAME")
            }
        }

        item {
            Text(
                "GameBoost X cannot guarantee a specific FPS or increase performance beyond your device's hardware. " +
                    "This analysis reflects real device conditions, not a simulation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}
