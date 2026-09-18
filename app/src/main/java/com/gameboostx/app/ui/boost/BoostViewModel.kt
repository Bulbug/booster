package com.gameboostx.app.ui.boost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gameboostx.app.data.BoostEngine
import com.gameboostx.app.data.DeviceInfoManager
import com.gameboostx.app.data.datastore.GameProfileStore
import com.gameboostx.app.data.model.BoostPlanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BoostUiState(
    val running: Boolean = true,
    val result: BoostPlanResult? = null,
)

class BoostViewModel(
    private val packageName: String,
    private val deviceInfoManager: DeviceInfoManager,
    private val boostEngine: BoostEngine,
    private val gameProfileStore: GameProfileStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BoostUiState())
    val uiState: StateFlow<BoostUiState> = _uiState.asStateFlow()

    init {
        runAnalysis()
    }

    fun runAnalysis() {
        viewModelScope.launch {
            _uiState.value = BoostUiState(running = true)
            val snapshot = withContext(Dispatchers.Default) { deviceInfoManager.snapshot() }
            // Shizuku isn't wired up until Phase 3 — always false here, never claimed otherwise.
            val result = boostEngine.analyze(snapshot, shizukuConnected = false)
            gameProfileStore.recordBoost(packageName, System.currentTimeMillis())
            _uiState.value = BoostUiState(running = false, result = result)
        }
    }
}
