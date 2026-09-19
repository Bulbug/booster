package com.gameboostx.app.data

import android.content.Context
import com.gameboostx.app.data.db.GameBoostDatabase
import com.gameboostx.app.data.db.LogEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single durable log for every logged operation in the app — Shizuku privileged actions and
 * gaming-session lifecycle events alike (spec §24/§33). Replaces the in-memory-only log used
 * during Phase 3.
 */
class LogRepository(context: Context) {
    private val dao = GameBoostDatabase.get(context).logDao()

    suspend fun append(message: String) {
        dao.insert(LogEntryEntity(atMillis = System.currentTimeMillis(), message = message))
        dao.trimTo(KEEP_COUNT)
    }

    fun observeRecentFormatted(limit: Int = 200): Flow<List<String>> =
        dao.observeRecent(limit).map { entries -> entries.map { format(it) } }

    private fun format(entry: LogEntryEntity): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.atMillis))
        return "$time  ${entry.message}"
    }

    companion object {
        private const val KEEP_COUNT = 300
    }
}
