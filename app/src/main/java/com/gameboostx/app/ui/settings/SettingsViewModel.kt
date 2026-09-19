package com.gameboostx.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gameboostx.app.data.BenchmarkManager
import com.gameboostx.app.data.BenchmarkResult
import com.gameboostx.app.data.ExportFormat
import com.gameboostx.app.data.NetworkDiagnosticsManager
import com.gameboostx.app.data.NetworkDiagnosticsResult
import com.gameboostx.app.data.OverlayPermissionHelper
import com.gameboostx.app.data.SessionExporter
import com.gameboostx.app.data.UsageAccessHelper
import com.gameboostx.app.data.datastore.AppSettings
import com.gameboostx.app.data.datastore.SettingsStore
import com.gameboostx.app.data.db.SessionDao
import com.gameboostx.app.shizuku.ShizukuManager
import com.gameboostx.app.shizuku.ShizukuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsUiState(
    val shizuku: ShizukuState = ShizukuState(),
    val appSettings: AppSettings = AppSettings(),
    val networkResult: NetworkDiagnosticsResult? = null,
    val networkTestRunning: Boolean = false,
    val benchmarkResult: BenchmarkResult? = null,
    val benchmarkRunning: Boolean = false,
    val sessionCount: Int = 0,
)

class SettingsViewModel(
    private val shizukuManager: ShizukuManager,
    private val settingsStore: SettingsStore,
    private val networkDiagnosticsManager: NetworkDiagnosticsManager,
    private val benchmarkManager: BenchmarkManager,
    private val sessionExporter: SessionExporter,
    private val sessionDao: SessionDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(shizukuManager.state, settingsStore.settings, sessionDao.observeSessions()) { s, settings, sessions ->
                Triple(s, settings, sessions.size)
            }.collect { (s, settings, count) ->
                _uiState.value = _uiState.value.copy(shizuku = s, appSettings = settings, sessionCount = count)
            }
        }
    }

    fun requestShizukuPermission() = shizukuManager.requestPermission()

    fun setUseShizuku(value: Boolean) = viewModelScope.launch { settingsStore.setUseShizuku(value) }
    fun setThermalWarnings(value: Boolean) = viewModelScope.launch { settingsStore.setThermalWarnings(value) }
    fun setAutoRestore(value: Boolean) = viewModelScope.launch { settingsStore.setAutoRestore(value) }
    fun setLogSessions(value: Boolean) = viewModelScope.launch { settingsStore.setLogSessions(value) }

    fun runNetworkTest() {
        if (_uiState.value.networkTestRunning) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(networkTestRunning = true)
            val result = networkDiagnosticsManager.runTest()
            _uiState.value = _uiState.value.copy(networkTestRunning = false, networkResult = result)
        }
    }

    fun runBenchmark() {
        if (_uiState.value.benchmarkRunning) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(benchmarkRunning = true)
            val result = benchmarkManager.run()
            _uiState.value = _uiState.value.copy(benchmarkRunning = false, benchmarkResult = result)
        }
    }

    suspend fun buildExportIntent(format: ExportFormat) =
        sessionExporter.export(sessionDao.observeSessions().first(), format)

    fun overlayGranted(context: Context) = OverlayPermissionHelper.isGranted(context)
    fun usageAccessGranted(context: Context) = UsageAccessHelper.isGranted(context)
}
