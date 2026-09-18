package com.gameboostx.app

import android.app.Application
import com.gameboostx.app.data.BoostEngine
import com.gameboostx.app.data.CapabilityManager
import com.gameboostx.app.data.DeviceInfoManager
import com.gameboostx.app.data.GameLibraryManager
import com.gameboostx.app.data.datastore.GameProfileStore

/**
 * Manual DI container. The dependency graph is small enough (Phase 1+2) that Hilt would be
 * pure ceremony — this gets revisited if/when Phase 3's ShizukuManager adds real lifecycle
 * complexity (service binding, disconnect handling).
 */
class GameBoostApplication : Application() {

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

    override fun onCreate() {
        super.onCreate()
        capabilityManager = CapabilityManager(this)
        deviceInfoManager = DeviceInfoManager(this, capabilityManager)
        gameLibraryManager = GameLibraryManager(this)
        boostEngine = BoostEngine()
        gameProfileStore = GameProfileStore(this)
    }
}
