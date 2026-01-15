package com.voiceink.android.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room entity representing a transcription history entry
 */
@Entity(tableName = "transcriptions")
data class TranscriptionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val modelId: String,
    val modelName: String,
    val provider: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Float,
    val wasStreaming: Boolean = false,
    val hadAutoPunctuation: Boolean = false
)
