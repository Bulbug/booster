package com.gameboostx.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gameboostx.app.data.CapabilityManager
import com.gameboostx.app.data.DeviceInfoManager
import com.gameboostx.app.data.PerformanceStatusEngine
import com.gameboostx.app.data.model.DeviceSnapshot
import com.gameboostx.app.data.model.PerformanceStatusResult
import com.gameboostx.app.shizuku.ShizukuManager
import com.gameboostx.app.shizuku.ShizukuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val snapshot: DeviceSnapshot? = null,
    val statusResult: PerformanceStatusResult? = null,
    val shizuku: ShizukuState = ShizukuState(),
    val loading: Boolean = true,
)

class HomeViewModel(
    private val deviceInfoManager: DeviceInfoManager,
    private val capabilityManager: CapabilityManager,
    private val shizukuManager: ShizukuManager,
    /** How often to re-read device state. Kept modest since polling /proc/stat and battery state has a real cost (spec §40). */
    private val pollIntervalMillis: Long = 3_000L,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var pollingStarted = false

    fun startPollingIfNeeded() {
        if (pollingStarted) return
        pollingStarted = true

        shizukuManager.state
            .onEach { shizukuState -> _uiState.value = _uiState.value.copy(shizuku = shizukuState) }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            while (isActive) {
                refreshOnce()
                kotlinx.coroutines.delay(pollIntervalMillis)
            }
        }
    }

    private suspend fun refreshOnce() {
        val snapshot = withContext(Dispatchers.Default) { deviceInfoManager.snapshot() }
        val status = PerformanceStatusEngine.evaluate(snapshot)
        _uiState.value = _uiState.value.copy(
            snapshot = snapshot,
            statusResult = status,
            loading = false,
        )
    }

    fun requestShizukuPermission() = shizukuManager.requestPermission()

    fun capabilitySummary(): Map<String, Boolean> = capabilityManager.summary()
}
