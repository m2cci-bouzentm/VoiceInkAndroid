package com.voiceink.android.data.history

import com.voiceink.android.data.database.TranscriptionDao
import com.voiceink.android.data.database.TranscriptionEntity
import com.voiceink.android.domain.model.TranscriptionModel
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing transcription history
 */
@Singleton
class TranscriptionHistoryRepository @Inject constructor(
    private val dao: TranscriptionDao
) {
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val BYTES_PER_SAMPLE = 2
        private const val CHANNELS = 1
    }

    /**
     * Get all transcriptions as a Flow
     */
    val allTranscriptions: Flow<List<TranscriptionEntity>> = dao.getAllFlow()

    /**
     * Get recent transcriptions with a limit
     */
    fun getRecent(limit: Int): Flow<List<TranscriptionEntity>> = dao.getRecentFlow(limit)

    /**
     * Get total count of transcriptions
     */
    val transcriptionCount: Flow<Int> = dao.getCountFlow()

    /**
     * Get total duration of all transcriptions
     */
    val totalDuration: Flow<Float> = dao.getTotalDurationFlow()

    /**
     * Search transcriptions by text content
     */
    fun search(query: String): Flow<List<TranscriptionEntity>> = dao.searchFlow(query)

    /**
     * Save a new transcription to history
     * @param text The transcribed text
     * @param model The model used for transcription
     * @param audioFile The audio file (for calculating duration)
     * @param wasStreaming Whether streaming transcription was used
     * @param hadAutoPunctuation Whether auto-punctuation was applied
     */
    suspend fun save(
        text: String,
        model: TranscriptionModel,
        audioFile: File?,
        wasStreaming: Boolean = false,
        hadAutoPunctuation: Boolean = false
    ) {
        val durationSeconds = audioFile?.let { getAudioDurationSeconds(it) } ?: 0f

        val entry = TranscriptionEntity(
            text = text,
            modelId = model.id,
            modelName = model.name,
            provider = model.provider.name,
            durationSeconds = durationSeconds,
            wasStreaming = wasStreaming,
            hadAutoPunctuation = hadAutoPunctuation
        )

        dao.insert(entry)
    }

    /**
     * Save a transcription with explicit duration
     */
    suspend fun saveWithDuration(
        text: String,
        model: TranscriptionModel,
        durationSeconds: Float,
        wasStreaming: Boolean = false,
        hadAutoPunctuation: Boolean = false
    ) {
        val entry = TranscriptionEntity(
            text = text,
            modelId = model.id,
            modelName = model.name,
            provider = model.provider.name,
            durationSeconds = durationSeconds,
            wasStreaming = wasStreaming,
            hadAutoPunctuation = hadAutoPunctuation
        )

        dao.insert(entry)
    }

    /**
     * Delete a transcription by ID
     */
    suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    /**
     * Delete a transcription entity
     */
    suspend fun delete(entry: TranscriptionEntity) {
        dao.delete(entry)
    }

    /**
     * Delete all transcriptions
     */
    suspend fun deleteAll() {
        dao.deleteAll()
    }

    /**
     * Get a transcription by ID
     */
    suspend fun getById(id: String): TranscriptionEntity? {
        return dao.getById(id)
    }

    /**
     * Calculate audio duration from WAV file
     */
    private fun getAudioDurationSeconds(audioFile: File): Float {
        if (!audioFile.exists() || audioFile.length() < 44) {
            return 0f
        }

        val audioDataSize = audioFile.length() - 44 // WAV header is 44 bytes
        val bytesPerSecond = SAMPLE_RATE * BYTES_PER_SAMPLE * CHANNELS
        return audioDataSize.toFloat() / bytesPerSecond
    }
}
