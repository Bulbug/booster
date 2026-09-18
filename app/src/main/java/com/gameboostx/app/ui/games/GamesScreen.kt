package com.gameboostx.app.ui.games

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.gameboostx.app.data.model.GameInfo
import com.gameboostx.app.data.model.ProfileType
import com.gameboostx.app.ui.theme.SurfaceElevated

@Composable
fun GamesScreen(
    viewModel: GamesViewModel,
    onBoostClick: (String) -> Unit,
    onLaunchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.scanIfNeeded() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add game manually")
            }
        }
    ) { padding ->
        when {
            state.loading -> Box(modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.scanError != null -> Box(modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Couldn't scan installed apps: ${state.scanError}")
            }
            state.games.isEmpty() -> Box(modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No games detected automatically.", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap + to add one by its package name.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> LazyColumn(
                modifier = modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.games, key = { it.packageName }) { game ->
                    GameCard(
                        game = game,
                        onProfileChange = { viewModel.setProfile(game.packageName, it) },
                        onBoostClick = { onBoostClick(game.packageName) },
                        onLaunchClick = { onLaunchClick(game.packageName) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddGameDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { pkg ->
                viewModel.addManualPackage(pkg)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun GameCard(
    game: GameInfo,
    onProfileChange: (ProfileType) -> Unit,
    onBoostClick: () -> Unit,
    onLaunchClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                game.icon?.let { drawable ->
                    Image(
                        bitmap = drawable.toBitmap(96, 96).asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(game.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(game.packageName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileDropdown(current = game.profile, onSelect = onProfileChange)
            }

            Text(
                text = game.lastOptimizedAtMillis?.let { "Last optimized: recently" } ?: "Never optimized",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBoostClick, modifier = Modifier.weight(1f)) { Text("BOOST") }
                OutlinedButton(onClick = onLaunchClick, modifier = Modifier.weight(1f)) { Text("LAUNCH") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileDropdown(current: ProfileType, onSelect: (ProfileType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Profile") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ProfileType.entries.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.label) },
                    onClick = {
                        onSelect(profile)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AddGameDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var packageName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add game by package name") },
        text = {
            OutlinedTextField(
                value = packageName,
                onValueChange = { packageName = it },
                label = { Text("e.g. com.example.game") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (packageName.isNotBlank()) onConfirm(packageName.trim()) }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
