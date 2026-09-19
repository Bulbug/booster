package com.gameboostx.app.session

import android.content.Context
import com.gameboostx.app.data.DeviceInfoManager
import com.gameboostx.app.data.LogRepository
import com.gameboostx.app.data.db.SessionDao
import com.gameboostx.app.data.db.SessionRecordEntity
import com.gameboostx.app.data.model.DeviceSnapshot
import com.gameboostx.app.data.model.ProfileType
import com.gameboostx.app.shizuku.GameMode
import com.gameboostx.app.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SessionUiState(
    val active: Boolean = false,
    val packageName: String? = null,
    val displayName: String? = null,
    val profile: ProfileType = ProfileType.BALANCED,
    val startedAtMillis: Long = 0,
    val elapsedSeconds: Long = 0,
    val startSnapshot: DeviceSnapshot? = null,
    val latestSnapshot: DeviceSnapshot? = null,
    val peakTempCelsius: Float? = null,
    val changesApplied: Int = 0,
    val exitDetected: Boolean = false,
)

/**
 * Owns the full lifecycle from spec §16/§21-22: snapshots device state, applies whatever the
 * chosen profile calls for (only if Shizuku is connected — Safe never touches the system),
 * monitors while the game runs, notices via ForegroundAppWatcher when it's no longer in the
 * foreground (spec §50), and restores + records a real session report on end (spec §32/§51).
 * This is a plain singleton-style manager (constructed once in GameBoostApplication) so
 * GamingSessionService and the UI observe the exact same state.
 */
class GamingSessionManager(
    context: Context,
    private val deviceInfoManager: DeviceInfoManager,
    private val shizukuManager: ShizukuManager,
    private val sessionDao: SessionDao,
    private val logRepository: LogRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val watcher = ForegroundAppWatcher(context)

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private var monitorJob: Job? = null
    private var watchJob: Job? = null

    fun startSession(packageName: String, displayName: String, profile: ProfileType) {
        if (_state.value.active) return
        val startSnapshot = deviceInfoManager.snapshot()

        _state.value = SessionUiState(
            active = true,
            packageName = packageName,
            displayName = displayName,
            profile = profile,
            startedAtMillis = System.currentTimeMillis(),
            startSnapshot = startSnapshot,
            latestSnapshot = startSnapshot,
            peakTempCelsius = startSnapshot.thermal.batteryTempCelsius,
        )
        log("Gaming session started: $displayName (${profile.label} profile)")

        scope.launch { applyProfileOptimizations(packageName, profile) }

        monitorJob = scope.launch {
            while (isActive) {
                delay(MONITOR_INTERVAL_MS)
                val snap = withContext(Dispatchers.Default) { deviceInfoManager.snapshot() }
                val currentPeak = _state.value.peakTempCelsius
                val newTemp = snap.thermal.batteryTempCelsius
                val peak = when {
                    newTemp == null -> currentPeak
                    currentPeak == null -> newTemp
                    else -> maxOf(currentPeak, newTemp)
                }
                _state.value = _state.value.copy(
                    latestSnapshot = snap,
                    peakTempCelsius = peak,
                    elapsedSeconds = (System.currentTimeMillis() - _state.value.startedAtMillis) / 1000,
                )
            }
        }

        watchJob = scope.launch {
            watcher.watch().collect { foreground ->
                val current = _state.value
                if (!current.active) return@collect
                if (foreground != null && foreground != packageName && !current.exitDetected) {
                    _state.value = current.copy(exitDetected = true)
                    log("Foreground moved away from $packageName — session appears to have ended.")
                } else if (foreground == packageName && current.exitDetected) {
                    _state.value = current.copy(exitDetected = false)
                }
            }
        }
    }

    /** Called when the user taps "Continue" on the exit-detected prompt (spec §50). */
    fun dismissExitDetected() {
        _state.value = _state.value.copy(exitDetected = false)
    }

    suspend fun endSession(reason: String): SessionRecordEntity? {
        val s = _state.value
        val pkg = s.packageName ?: return null
        if (!s.active) return null

        monitorJob?.cancel()
        watchJob?.cancel()

        var restored = s.changesApplied
        if (shizukuManager.state.value.serviceBound) {
            if (shizukuManager.restoreRefreshRate()) restored++
            if (shizukuManager.setGameMode(pkg, GameMode.STANDARD)) restored++
        }

        val endSnapshot = withContext(Dispatchers.Default) { deviceInfoManager.snapshot() }
        val record = SessionRecordEntity(
            packageName = pkg,
            displayName = s.displayName ?: pkg,
            profile = s.profile.name,
            startedAtMillis = s.startedAtMillis,
            endedAtMillis = System.currentTimeMillis(),
            startTempCelsius = s.startSnapshot?.thermal?.batteryTempCelsius,
            endTempCelsius = endSnapshot.thermal.batteryTempCelsius,
            peakTempCelsius = s.peakTempCelsius,
            startRamUsedBytes = s.startSnapshot?.memory?.usedBytes ?: -1L,
            endRamUsedBytes = endSnapshot.memory.usedBytes,
            startBatteryPercent = s.startSnapshot?.battery?.percent ?: -1,
            endBatteryPercent = endSnapshot.battery.percent,
            refreshRateHz = endSnapshot.display.currentRefreshRateHz,
            thermalStatusAtEnd = endSnapshot.thermal.status.name,
            changesApplied = restored,
            endedReason = reason,
        )
        sessionDao.insertSession(record)
        log("Session ended ($reason): ${record.displayName}, ${(record.endedAtMillis - record.startedAtMillis) / 60000} min.")

        _state.value = SessionUiState()
        return record
    }

    private suspend fun applyProfileOptimizations(packageName: String, profile: ProfileType) {
        if (!shizukuManager.state.value.serviceBound) return
        var applied = 0
        when (profile) {
            ProfileType.PERFORMANCE -> {
                val maxHz = _state.value.startSnapshot?.display?.supportedRefreshRatesHz?.maxOrNull()
                if (maxHz != null && shizukuManager.applyPeakRefreshRate(maxHz)) applied++
                if (shizukuManager.setGameMode(packageName, GameMode.PERFORMANCE)) applied++
            }
            ProfileType.BALANCED -> {
                if (shizukuManager.setGameMode(packageName, GameMode.STANDARD)) applied++
            }
            ProfileType.SAFE, ProfileType.CUSTOM -> {
                // Safe applies nothing automatically; Custom is left to explicit Boost-screen actions.
            }
        }
        if (applied > 0) {
            _state.value = _state.value.copy(changesApplied = _state.value.changesApplied + applied)
        }
    }

    private fun log(message: String) {
        scope.launch { logRepository.append(message) }
    }

    companion object {
        private const val MONITOR_INTERVAL_MS = 5_000L
    }
}
