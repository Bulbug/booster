package com.gameboostx.app.ui.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gameboostx.app.data.GameLibraryManager
import com.gameboostx.app.data.datastore.GameProfileStore
import com.gameboostx.app.data.datastore.SettingsStore
import com.gameboostx.app.data.model.GameInfo
import com.gameboostx.app.data.model.ProfileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GamesUiState(
    val games: List<GameInfo> = emptyList(),
    val loading: Boolean = true,
    val scanError: String? = null,
)

class GamesViewModel(
    private val gameLibraryManager: GameLibraryManager,
    private val gameProfileStore: GameProfileStore,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GamesUiState())
    val uiState: StateFlow<GamesUiState> = _uiState.asStateFlow()

    private var scanned = false

    fun scanIfNeeded() {
        if (scanned) return
        scanned = true
        rescan()
    }

    fun rescan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, scanError = null)
            try {
                val found = withContext(Dispatchers.Default) { gameLibraryManager.scanInstalledGames() }
                // Screen 6 of the setup wizard lets the person pick this default once, up front,
                // rather than every newly-detected game silently landing on a hardcoded Balanced.
                val defaultProfile = settingsStore.settings.first().defaultProfile
                val withProfiles = found.map { game ->
                    val profile = gameProfileStore.profileFor(game.packageName, fallback = defaultProfile).first()
                    val lastBoost = gameProfileStore.lastBoostAtFor(game.packageName).first()
                    game.copy(profile = profile, lastOptimizedAtMillis = lastBoost)
                }
                _uiState.value = GamesUiState(games = withProfiles, loading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, scanError = e.message ?: "Scan failed")
            }
        }
    }

    fun addManualPackage(packageName: String) {
        val added = gameLibraryManager.resolveManualPackage(packageName) ?: return
        if (_uiState.value.games.any { it.packageName == added.packageName }) return
        _uiState.value = _uiState.value.copy(games = (_uiState.value.games + added).sortedBy { it.displayName.lowercase() })
    }

    fun setProfile(packageName: String, profile: ProfileType) {
        viewModelScope.launch {
            gameProfileStore.setProfile(packageName, profile)
            _uiState.value = _uiState.value.copy(
                games = _uiState.value.games.map { if (it.packageName == packageName) it.copy(profile = profile) else it }
            )
        }
    }
}
