package com.gameboostx.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per finished gaming session. Every field here was actually measured at session
 * start/end — nothing is a placeholder, and FPS is deliberately absent because this app has
 * no reliable source for another app's real frame rate (spec §24/§51).
 */
@Entity(tableName = "session_records")
data class SessionRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val displayName: String,
    val profile: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val startTempCelsius: Float?,
    val endTempCelsius: Float?,
    val peakTempCelsius: Float?,
    val startRamUsedBytes: Long,
    val endRamUsedBytes: Long,
    val startBatteryPercent: Int,
    val endBatteryPercent: Int,
    val refreshRateHz: Float,
    val thermalStatusAtEnd: String,
    val changesApplied: Int,
    val endedReason: String, // "manual" | "auto_detected_exit" | "shizuku_disconnected" etc.
)
