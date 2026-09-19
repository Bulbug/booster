package com.gameboostx.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Durable version of the operation log — replaces the in-memory-only log from Phase 3. */
@Entity(tableName = "log_entries")
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMillis: Long,
    val message: String,
)
