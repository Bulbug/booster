package com.gameboostx.app.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gameboostx.app.data.model.ProfileType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gameProfileDataStore by preferencesDataStore(name = "game_profiles")

/**
 * Persists only what the user actually set: a profile per package and a last-optimized
 * timestamp per package. No telemetry, no history, nothing uploaded (spec §27/§37).
 */
class GameProfileStore(private val context: Context) {

    private fun profileKey(packageName: String) = stringPreferencesKey("profile_$packageName")
    private fun lastBoostKey(packageName: String) = longPreferencesKey("last_boost_$packageName")

    fun profileFor(packageName: String): Flow<ProfileType> =
        context.gameProfileDataStore.data.map { prefs ->
            prefs[profileKey(packageName)]?.let { stored ->
                ProfileType.entries.find { it.name == stored }
            } ?: ProfileType.BALANCED
        }

    fun lastBoostAtFor(packageName: String): Flow<Long?> =
        context.gameProfileDataStore.data.map { prefs -> prefs[lastBoostKey(packageName)] }

    suspend fun setProfile(packageName: String, profile: ProfileType) {
        context.gameProfileDataStore.edit { it[profileKey(packageName)] = profile.name }
    }

    suspend fun recordBoost(packageName: String, atMillis: Long) {
        context.gameProfileDataStore.edit { it[lastBoostKey(packageName)] = atMillis }
    }
}
