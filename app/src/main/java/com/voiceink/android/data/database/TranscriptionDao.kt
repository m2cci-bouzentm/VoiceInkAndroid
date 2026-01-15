package com.voiceink.android.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for transcription history
 */
@Dao
interface TranscriptionDao {

    /**
     * Get all transcriptions ordered by timestamp (newest first)
     */
    @Query("SELECT * FROM transcriptions ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<TranscriptionEntity>>

    /**
     * Get recent transcriptions with a limit
     */
    @Query("SELECT * FROM transcriptions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentFlow(limit: Int): Flow<List<TranscriptionEntity>>

    /**
     * Get a single transcription by ID
     */
    @Query("SELECT * FROM transcriptions WHERE id = :id")
    suspend fun getById(id: String): TranscriptionEntity?

    /**
     * Search transcriptions by text content
     */
    @Query("SELECT * FROM transcriptions WHERE text LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchFlow(query: String): Flow<List<TranscriptionEntity>>

    /**
     * Get count of all transcriptions
     */
    @Query("SELECT COUNT(*) FROM transcriptions")
    fun getCountFlow(): Flow<Int>

    /**
     * Get total duration of all transcriptions in seconds
     */
    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM transcriptions")
    fun getTotalDurationFlow(): Flow<Float>

    /**
     * Insert a new transcription
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TranscriptionEntity): Long

    /**
     * Delete a transcription
     */
    @Delete
    suspend fun delete(entry: TranscriptionEntity)

    /**
     * Delete a transcription by ID
     */
    @Query("DELETE FROM transcriptions WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Delete all transcriptions
     */
    @Query("DELETE FROM transcriptions")
    suspend fun deleteAll()

    /**
     * Get transcriptions by provider (for statistics)
     */
    @Query("SELECT * FROM transcriptions WHERE provider = :provider ORDER BY timestamp DESC")
    fun getByProviderFlow(provider: String): Flow<List<TranscriptionEntity>>
}
