package com.voiceink.android.domain.transcription

import com.voiceink.android.domain.model.TranscriptionModel
import java.io.File

/**
 * Result of a transcription operation
 */
sealed class TranscriptionResult {
    data class Success(val text: String) : TranscriptionResult()
    data class Error(val message: String, val cause: Throwable? = null) : TranscriptionResult()
}

/**
 * Interface for all transcription services
 */
interface TranscriptionService {
    /**
     * Transcribe audio from a file
     * @param audioFile The audio file to transcribe (WAV format, 16kHz, mono)
     * @param model The model to use for transcription
     * @param language Optional language code for transcription (e.g., "en", "es", "auto")
     * @return TranscriptionResult containing the transcribed text or error
     */
    suspend fun transcribe(
        audioFile: File,
        model: TranscriptionModel,
        language: String = "auto"
    ): TranscriptionResult
}
