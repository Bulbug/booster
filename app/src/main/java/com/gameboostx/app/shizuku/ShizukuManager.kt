package com.gameboostx.app.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.gameboostx.app.data.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class ShizukuState(
    val binderAvailable: Boolean = false, // Shizuku app is installed and its service is running
    val permissionGranted: Boolean = false,
    val serviceBound: Boolean = false,    // our PrivilegedService is actually connected
)

enum class GameMode(val value: Int) { STANDARD(1), PERFORMANCE(2), BATTERY(3) }

/**
 * Wraps the official Shizuku API end to end: detects the binder, walks the permission flow,
 * binds our PrivilegedService (spec §12-§14), and recovers cleanly if Shizuku disconnects
 * mid-session (spec §44) — advanced controls just become unavailable, nothing crashes.
 *
 * Every privileged call here is confirmed by the caller (see ShizukuConfirmDialog) before it
 * runs — this class doesn't ask for confirmation itself, it just executes and logs to the
 * durable LogRepository (spec §13/§24).
 */
class ShizukuManager(
    private val context: Context,
    private val logRepository: LogRepository,
) {

    private val _state = MutableStateFlow(ShizukuState())
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var service: IPrivilegedService? = null
    private var started = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refreshBinderState() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        service = null
        _state.value = _state.value.copy(binderAvailable = false, serviceBound = false)
        appendLog("Shizuku connection lost. Advanced controls are temporarily unavailable.")
    }
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        _state.value = _state.value.copy(permissionGranted = granted)
        appendLog(if (granted) "Shizuku permission granted." else "Shizuku permission denied.")
        if (granted) bindPrivilegedService()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IPrivilegedService.Stub.asInterface(binder)
            _state.value = _state.value.copy(serviceBound = true)
            appendLog("Privileged service connected.")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            _state.value = _state.value.copy(serviceBound = false)
            appendLog("Privileged service disconnected.")
        }
    }

    /** Call once (e.g. from Application.onCreate) — safe to call more than once. */
    fun start() {
        if (started) return
        started = true
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        refreshBinderState()
    }

    fun stop() {
        if (!started) return
        started = false
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        unbindPrivilegedService()
        logScope.cancel()
    }

    private fun refreshBinderState() {
        val available = try { Shizuku.pingBinder() } catch (_: Exception) { false }
        val granted = available && try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
        _state.value = _state.value.copy(binderAvailable = available, permissionGranted = granted)
        if (available && granted) bindPrivilegedService()
    }

    /** Triggers Android's Shizuku permission prompt. Result arrives via permissionResultListener. */
    fun requestPermission() {
        if (!_state.value.binderAvailable) return
        try {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        } catch (e: Exception) {
            appendLog("Could not request Shizuku permission: ${e.message}")
        }
    }

    private fun serviceComponent() = ComponentName(context.packageName, PrivilegedService::class.java.name)

    private fun bindPrivilegedService() {
        if (_state.value.serviceBound) return
        try {
            val args = Shizuku.UserServiceArgs(serviceComponent())
                .daemon(false)
                .processNameSuffix("privileged")
                .debuggable(false)
                .version(1)
            Shizuku.bindUserService(args, connection)
        } catch (e: Exception) {
            appendLog("Failed to bind privileged service: ${e.message}")
        }
    }

    private fun unbindPrivilegedService() {
        if (!_state.value.serviceBound && service == null) return
        try {
            val args = Shizuku.UserServiceArgs(serviceComponent())
            Shizuku.unbindUserService(args, connection, true)
        } catch (_: Exception) {
            // best-effort — Shizuku itself disconnecting will also clear this via binderDeadListener
        }
        service = null
        _state.value = _state.value.copy(serviceBound = false)
    }

    // ---- Privileged operations (each is one explicit, named, reversible action) ----

    suspend fun applyPeakRefreshRate(hz: Float): Boolean = withContext(Dispatchers.IO) {
        val svc = service
        if (svc == null) {
            appendLog("Refresh rate change skipped — Shizuku not connected.")
            return@withContext false
        }
        val result = try { svc.setPeakRefreshRate(hz) } catch (_: Exception) { false }
        appendLog(if (result) "Refresh rate locked to ${hz.toInt()}Hz." else "Refresh rate change failed or unsupported on this device.")
        result
    }

    suspend fun restoreRefreshRate(): Boolean = withContext(Dispatchers.IO) {
        val svc = service
        if (svc == null) {
            appendLog("Refresh rate restore skipped — Shizuku not connected.")
            return@withContext false
        }
        val result = try { svc.clearRefreshRateOverride() } catch (_: Exception) { false }
        appendLog(if (result) "Refresh rate override cleared." else "Failed to clear refresh rate override.")
        result
    }

    suspend fun setGameMode(packageName: String, mode: GameMode): Boolean = withContext(Dispatchers.IO) {
        val svc = service
        if (svc == null) {
            appendLog("Game Mode change skipped — Shizuku not connected.")
            return@withContext false
        }
        val result = try { svc.setGameMode(packageName, mode.value) } catch (_: Exception) { false }
        appendLog(if (result) "Game Mode set to ${mode.name} for $packageName." else "Game Mode change failed or unsupported for $packageName.")
        result
    }

    suspend fun currentGameMode(packageName: String): GameMode? = withContext(Dispatchers.IO) {
        val svc = service ?: return@withContext null
        val value = try { svc.getGameMode(packageName) } catch (_: Exception) { -1 }
        GameMode.entries.find { it.value == value }
    }

    private fun appendLog(line: String) {
        logScope.launch { logRepository.append(line) }
    }

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 9001
    }
}
