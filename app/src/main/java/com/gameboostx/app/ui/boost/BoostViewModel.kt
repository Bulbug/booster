package com.gameboostx.app.ui.boost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gameboostx.app.data.BoostEngine
import com.gameboostx.app.data.DeviceInfoManager
import com.gameboostx.app.data.datastore.GameProfileStore
import com.gameboostx.app.data.model.BoostPlanResult
import com.gameboostx.app.shizuku.GameMode
import com.gameboostx.app.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a pending privileged action will do — shown in the explain-then-confirm dialog (spec §13). */
data class PendingShizukuAction(
    val title: String,
    val what: String,
    val why: String,
    val mayChange: String,
    val howToRestore: String,
    val confirm: suspend () -> Boolean,
)

data class BoostUiState(
    val running: Boolean = true,
    val result: BoostPlanResult? = null,
    val shizukuConnected: Boolean = false,
    val maxRefreshHz: Float? = null,
    val pendingAction: PendingShizukuAction? = null,
    val lastActionMessage: String? = null,
)

class BoostViewModel(
    private val packageName: String,
    private val deviceInfoManager: DeviceInfoManager,
    private val boostEngine: BoostEngine,
    private val gameProfileStore: GameProfileStore,
    private val shizukuManager: ShizukuManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BoostUiState())
    val uiState: StateFlow<BoostUiState> = _uiState.asStateFlow()

    init {
        shizukuManager.state
            .onEach { s -> _uiState.value = _uiState.value.copy(shizukuConnected = s.serviceBound) }
            .launchIn(viewModelScope)
        runAnalysis()
    }

    fun runAnalysis() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(running = true)
            val snapshot = withContext(Dispatchers.Default) { deviceInfoManager.snapshot() }
            val connected = _uiState.value.shizukuConnected
            val result = boostEngine.analyze(snapshot, shizukuConnected = connected)
            gameProfileStore.recordBoost(packageName, System.currentTimeMillis())
            _uiState.value = _uiState.value.copy(
                running = false,
                result = result,
                maxRefreshHz = snapshot.display.supportedRefreshRatesHz.maxOrNull(),
            )
        }
    }

    // ---- Refresh rate ----

    fun requestApplyMaxRefreshRate() {
        val hz = _uiState.value.maxRefreshHz ?: return
        _uiState.value = _uiState.value.copy(
            pendingAction = PendingShizukuAction(
                title = "Lock refresh rate to ${hz.toInt()}Hz",
                what = "Sets Android's min and peak refresh rate to ${hz.toInt()}Hz via Settings.System (the same setting Display settings uses).",
                why = "This game supports up to ${hz.toInt()}Hz but the display isn't currently running at it.",
                mayChange = "The refresh rate for the whole device, not just this game, until you restore it.",
                howToRestore = "Tap Restore Refresh Rate below, or change it manually in Display settings.",
                confirm = { shizukuManager.applyPeakRefreshRate(hz) },
            )
        )
    }

    fun requestRestoreRefreshRate() {
        _uiState.value = _uiState.value.copy(
            pendingAction = PendingShizukuAction(
                title = "Restore refresh rate",
                what = "Clears the min/peak refresh rate override.",
                why = "Returns the display to Android's own default refresh-rate behavior.",
                mayChange = "The refresh rate for the whole device.",
                howToRestore = "This is itself the restore action — nothing further to undo.",
                confirm = { shizukuManager.restoreRefreshRate() },
            )
        )
    }

    // ---- Game Mode ----

    fun requestSetGameMode(mode: GameMode) {
        _uiState.value = _uiState.value.copy(
            pendingAction = PendingShizukuAction(
                title = "Set Game Mode: ${mode.name}",
                what = "Sets Android's per-app Game Mode for this game via the documented `cmd game` interface.",
                why = when (mode) {
                    GameMode.PERFORMANCE -> "Asks Android to prioritize this game's performance over battery life while it's running."
                    GameMode.BATTERY -> "Asks Android to prioritize battery life over peak performance while this game is running."
                    GameMode.STANDARD -> "Returns this game to Android's default, unmodified Game Mode behavior."
                },
                mayChange = "Only this game's package — Game Mode is per-app, unlike refresh rate.",
                howToRestore = "Set it back to Standard any time from this screen.",
                confirm = { shizukuManager.setGameMode(packageName, mode) },
            )
        )
    }

    fun cancelPendingAction() {
        _uiState.value = _uiState.value.copy(pendingAction = null)
    }

    fun confirmPendingAction() {
        val action = _uiState.value.pendingAction ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(pendingAction = null)
            val success = action.confirm()
            _uiState.value = _uiState.value.copy(
                lastActionMessage = if (success) "${action.title} — done." else "${action.title} — failed or unsupported on this device.",
            )
            runAnalysis()
        }
    }

    fun dismissLastActionMessage() {
        _uiState.value = _uiState.value.copy(lastActionMessage = null)
    }
}
