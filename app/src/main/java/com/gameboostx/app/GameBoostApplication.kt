package com.gameboostx.app

import android.app.Application
import com.gameboostx.app.data.BenchmarkManager
import com.gameboostx.app.data.BoostEngine
import com.gameboostx.app.data.CapabilityManager
import com.gameboostx.app.data.DeviceInfoManager
import com.gameboostx.app.data.GameLibraryManager
import com.gameboostx.app.data.LogRepository
import com.gameboostx.app.data.NetworkDiagnosticsManager
import com.gameboostx.app.data.SessionExporter
import com.gameboostx.app.data.datastore.GameProfileStore
import com.gameboostx.app.data.datastore.OnboardingStore
import com.gameboostx.app.data.datastore.SettingsStore
import com.gameboostx.app.data.db.GameBoostDatabase
import com.gameboostx.app.overlay.OverlayController
import com.gameboostx.app.session.GamingSessionManager
import com.gameboostx.app.session.GamingSessionService
import com.gameboostx.app.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Manual DI container. Still small enough that Hilt would be pure ceremony — ShizukuManager's
 * and GamingSessionManager's lifecycles are handled inside those classes themselves.
 */
class GameBoostApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var capabilityManager: CapabilityManager
        private set
    lateinit var deviceInfoManager: DeviceInfoManager
        private set
    lateinit var gameLibraryManager: GameLibraryManager
        private set
    lateinit var boostEngine: BoostEngine
        private set
    lateinit var gameProfileStore: GameProfileStore
        private set
    lateinit var shizukuManager: ShizukuManager
        private set
    lateinit var logRepository: LogRepository
        private set
    lateinit var database: GameBoostDatabase
        private set
    lateinit var gamingSessionManager: GamingSessionManager
        private set
    lateinit var overlayController: OverlayController
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var networkDiagnosticsManager: NetworkDiagnosticsManager
        private set
    lateinit var benchmarkManager: BenchmarkManager
        private set
    lateinit var sessionExporter: SessionExporter
        private set
    lateinit var onboardingStore: OnboardingStore
        private set

    /** Set right before navigating to the Session screen — a simple way to pass a full GameInfo
     * without threading it through nav args as a string. Read once by SessionScreen. */
    var pendingSessionGame: com.gameboostx.app.data.model.GameInfo? = null

    override fun onCreate() {
        super.onCreate()
        capabilityManager = CapabilityManager(this)
        deviceInfoManager = DeviceInfoManager(this, capabilityManager)
        gameLibraryManager = GameLibraryManager(this)
        boostEngine = BoostEngine()
        gameProfileStore = GameProfileStore(this)

        database = GameBoostDatabase.get(this)
        logRepository = LogRepository(this)

        shizukuManager = ShizukuManager(this, logRepository)
        shizukuManager.start()

        gamingSessionManager = GamingSessionManager(
            this, deviceInfoManager, shizukuManager, database.sessionDao(), logRepository,
        )
        overlayController = OverlayController(this)
        settingsStore = SettingsStore(this)
        networkDiagnosticsManager = NetworkDiagnosticsManager(this)
        benchmarkManager = BenchmarkManager(this, deviceInfoManager)
        sessionExporter = SessionExporter(this)
        onboardingStore = OnboardingStore(this)

        // Keep GamingSessionService's lifecycle in lockstep with the session itself — never
        // running longer than the thing it's reporting on (spec §40).
        gamingSessionManager.state
            .onEach { state ->
                if (state.active) GamingSessionService.start(this) else GamingSessionService.stop(this)
                if (state.active && overlayController.isShowing) overlayController.update(state)
                if (!state.active) overlayController.hide()
            }
            .launchIn(appScope)
    }
}
