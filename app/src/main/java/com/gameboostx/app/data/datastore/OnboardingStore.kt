package com.gameboostx.app.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")

class OnboardingStore(private val context: Context) {
    private val key = booleanPreferencesKey("completed")

    /** Null until the first read completes — MainActivity shows a brief loading state for that gap rather than flashing the wizard. */
    val completed: Flow<Boolean> = context.onboardingDataStore.data.map { it[key] ?: false }

    suspend fun markCompleted() {
        context.onboardingDataStore.edit { it[key] = true }
    }
}
