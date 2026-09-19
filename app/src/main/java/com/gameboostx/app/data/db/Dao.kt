package com.gameboostx.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(record: SessionRecordEntity): Long

    @Query("SELECT * FROM session_records ORDER BY startedAtMillis DESC")
    fun observeSessions(): Flow<List<SessionRecordEntity>>

    @Query("SELECT * FROM session_records WHERE packageName = :packageName ORDER BY startedAtMillis DESC")
    fun observeSessionsForGame(packageName: String): Flow<List<SessionRecordEntity>>

    @Query("DELETE FROM session_records")
    suspend fun clearAll()
}

@Dao
interface LogDao {
    @Insert
    suspend fun insert(entry: LogEntryEntity)

    @Query("SELECT * FROM log_entries ORDER BY atMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<LogEntryEntity>>

    @Query("DELETE FROM log_entries WHERE id NOT IN (SELECT id FROM log_entries ORDER BY atMillis DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)
}
