package com.gameboostx.app.ui.advanced

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gameboostx.app.data.CapabilityManager
import com.gameboostx.app.data.LogRepository
import com.gameboostx.app.data.OemGameModeInfo
import com.gameboostx.app.data.OemGameModeManager
import com.gameboostx.app.data.db.SessionRecordEntity
import com.gameboostx.app.data.db.SessionDao
import com.gameboostx.app.shizuku.ShizukuManager
import com.gameboostx.app.shizuku.ShizukuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class AdvancedUiState(
    val shizuku: ShizukuState = ShizukuState(),
    val log: List<String> = emptyList(),
    val capabilities: Map<String, Boolean> = emptyMap(),
    val recentSessions: List<SessionRecordEntity> = emptyList(),
    val oemGameMode: OemGameModeInfo? = null,
)

class AdvancedViewModel(
    private val shizukuManager: ShizukuManager,
    private val capabilityManager: CapabilityManager,
    private val logRepository: LogRepository,
    private val sessionDao: SessionDao,
    private val oemGameModeManager: OemGameModeManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AdvancedUiState(capabilities = capabilityManager.summary(), oemGameMode = oemGameModeManager.detect())
    )
    val uiState: StateFlow<AdvancedUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                shizukuManager.state,
                logRepository.observeRecentFormatted(limit = 100),
                sessionDao.observeSessions(),
            ) { state, log, sessions -> Triple(state, log, sessions) }
                .collect { (state, log, sessions) ->
                    _uiState.value = _uiState.value.copy(shizuku = state, log = log, recentSessions = sessions)
                }
        }
    }

    fun requestPermission() = shizukuManager.requestPermission()

    fun oemLaunchIntent(packageName: String) = oemGameModeManager.launchIntent(packageName)
}
