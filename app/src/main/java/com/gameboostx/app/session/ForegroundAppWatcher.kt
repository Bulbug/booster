package com.gameboostx.app.session

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.gameboostx.app.data.UsageAccessHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Polls UsageStatsManager's real event log to find the current foreground package — there is
 * no push-based "foreground app changed" API available without special/root access, so a
 * queryEvents poll is the legitimate mechanism (same one Digital Wellbeing-style apps use).
 *
 * The interval is intentionally not aggressive: continuous sub-second polling was the exact
 * mistake that made an earlier project's tweaks cause more lag than they fixed by burning CPU
 * during gameplay itself — this watcher polls every [intervalMillis] instead.
 */
class ForegroundAppWatcher(
    private val context: Context,
    private val intervalMillis: Long = 4_000L,
) {
    private val usageStatsManager: UsageStatsManager
        get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /** Emits the current foreground package each poll, or null if it can't be determined (no permission, or nothing resolved). */
    fun watch(): Flow<String?> = flow {
        var lastQueryEnd = System.currentTimeMillis()
        while (true) {
            if (!UsageAccessHelper.isGranted(context)) {
                emit(null)
            } else {
                val now = System.currentTimeMillis()
                val foreground = queryForegroundPackage(lastQueryEnd - LOOKBACK_MARGIN_MS, now)
                lastQueryEnd = now
                emit(foreground)
            }
            delay(intervalMillis)
        }
    }

    private fun queryForegroundPackage(startMillis: Long, endMillis: Long): String? {
        val events = try {
            usageStatsManager.queryEvents(startMillis, endMillis)
        } catch (_: Exception) {
            return null
        }
        var lastResumed: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                lastResumed = event.packageName
            } else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_PAUSED
            ) {
                if (event.packageName == lastResumed) lastResumed = null
            }
        }
        return lastResumed
    }

    companion object {
        private const val LOOKBACK_MARGIN_MS = 2_000L // small overlap so no event is missed between polls
    }
}
