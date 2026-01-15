package com.voiceink.android.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for VoiceInk app
 */
@Database(
    entities = [TranscriptionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VoiceInkDatabase : RoomDatabase() {

    abstract fun transcriptionDao(): TranscriptionDao

    companion object {
        private const val DATABASE_NAME = "voiceink_database"

        @Volatile
        private var INSTANCE: VoiceInkDatabase? = null

        fun getInstance(context: Context): VoiceInkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoiceInkDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
