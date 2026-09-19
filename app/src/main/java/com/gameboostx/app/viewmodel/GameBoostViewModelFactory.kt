package com.gameboostx.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gameboostx.app.GameBoostApplication
import com.gameboostx.app.ui.advanced.AdvancedViewModel
import com.gameboostx.app.ui.boost.BoostViewModel
import com.gameboostx.app.ui.games.GamesViewModel
import com.gameboostx.app.ui.home.HomeViewModel
import com.gameboostx.app.ui.session.SessionViewModel
import com.gameboostx.app.ui.settings.SettingsViewModel

/**
 * Hand-rolled factory since the app has no DI framework (see GameBoostApplication). The optional
 * [packageName] is only needed for BoostViewModel, which is created per-game.
 */
class GameBoostViewModelFactory(
    private val app: GameBoostApplication,
    private val packageName: String? = null,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            HomeViewModel::class.java ->
                HomeViewModel(app.deviceInfoManager, app.capabilityManager, app.shizukuManager) as T
            GamesViewModel::class.java ->
                GamesViewModel(app.gameLibraryManager, app.gameProfileStore) as T
            BoostViewModel::class.java -> {
                requireNotNull(packageName) { "BoostViewModel requires a packageName" }
                BoostViewModel(packageName, app.deviceInfoManager, app.boostEngine, app.gameProfileStore, app.shizukuManager) as T
            }
            AdvancedViewModel::class.java ->
                AdvancedViewModel(app.shizukuManager, app.capabilityManager, app.logRepository, app.database.sessionDao()) as T
            SessionViewModel::class.java ->
                SessionViewModel(app.gamingSessionManager, app.overlayController) as T
            SettingsViewModel::class.java ->
                SettingsViewModel(
                    app.shizukuManager,
                    app.settingsStore,
                    app.networkDiagnosticsManager,
                    app.benchmarkManager,
                    app.sessionExporter,
                    app.database.sessionDao(),
                ) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
