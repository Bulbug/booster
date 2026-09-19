package com.gameboostx.app.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val useShizukuWhenAvailable: Boolean = true,
    val showThermalWarnings: Boolean = true,
    val restoreSettingsAutomatically: Boolean = true,
    val logGamingSessions: Boolean = true,
)

/** General behavior toggles (spec §36) — none of these unlock anything the app wouldn't otherwise ask permission for; they just change defaults. */
class SettingsStore(private val context: Context) {

    private object Keys {
        val USE_SHIZUKU = booleanPreferencesKey("use_shizuku_when_available")
        val THERMAL_WARNINGS = booleanPreferencesKey("show_thermal_warnings")
        val AUTO_RESTORE = booleanPreferencesKey("restore_settings_automatically")
        val LOG_SESSIONS = booleanPreferencesKey("log_gaming_sessions")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            useShizukuWhenAvailable = prefs[Keys.USE_SHIZUKU] ?: true,
            showThermalWarnings = prefs[Keys.THERMAL_WARNINGS] ?: true,
            restoreSettingsAutomatically = prefs[Keys.AUTO_RESTORE] ?: true,
            logGamingSessions = prefs[Keys.LOG_SESSIONS] ?: true,
        )
    }

    suspend fun setUseShizuku(value: Boolean) = context.settingsDataStore.edit { it[Keys.USE_SHIZUKU] = value }
    suspend fun setThermalWarnings(value: Boolean) = context.settingsDataStore.edit { it[Keys.THERMAL_WARNINGS] = value }
    suspend fun setAutoRestore(value: Boolean) = context.settingsDataStore.edit { it[Keys.AUTO_RESTORE] = value }
    suspend fun setLogSessions(value: Boolean) = context.settingsDataStore.edit { it[Keys.LOG_SESSIONS] = value }
}
