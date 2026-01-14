package com.voiceink.android.domain.transcription

import com.voiceink.android.domain.model.ModelProvider
import com.voiceink.android.domain.model.TranscriptionModel
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes transcription requests to the appropriate service based on model provider
 */
@Singleton
class TranscriptionRegistry @Inject constructor(
    private val localService: LocalTranscriptionService,
    private val geminiService: GeminiTranscriptionService,
    private val openaiService: OpenAITranscriptionService
) : TranscriptionService {

    override suspend fun transcribe(audioFile: File, model: TranscriptionModel): TranscriptionResult {
        val service = when (model.provider) {
            ModelProvider.LOCAL -> localService
            ModelProvider.GEMINI -> geminiService
            ModelProvider.OPENAI -> openaiService
        }
        return service.transcribe(audioFile, model)
    }
}
