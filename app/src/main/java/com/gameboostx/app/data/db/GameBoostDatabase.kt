package com.gameboostx.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SessionRecordEntity::class, LogEntryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class GameBoostDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun logDao(): LogDao

    companion object {
        @Volatile private var instance: GameBoostDatabase? = null

        fun get(context: Context): GameBoostDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GameBoostDatabase::class.java,
                    "gameboostx.db",
                ).build().also { instance = it }
            }
    }
}
