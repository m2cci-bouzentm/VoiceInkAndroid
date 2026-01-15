package com.voiceink.android.domain.transcription

import com.voiceink.android.data.preferences.UsageRepository
import com.voiceink.android.data.subscription.SubscriptionRepository
import com.voiceink.android.domain.model.ModelProvider
import com.voiceink.android.domain.model.TranscriptionModel
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes transcription requests to the appropriate service based on model provider.
 * Also enforces usage limits for free tier users.
 */
@Singleton
class TranscriptionRegistry @Inject constructor(
    private val localService: LocalTranscriptionService,
    private val geminiService: GeminiTranscriptionService,
    private val openaiService: OpenAITranscriptionService,
    private val usageRepository: UsageRepository,
    private val subscriptionRepository: SubscriptionRepository
) : TranscriptionService {

    override suspend fun transcribe(audioFile: File, model: TranscriptionModel): TranscriptionResult {
        val isLocal = model.provider == ModelProvider.LOCAL
        val isPro = subscriptionRepository.isPro

        // Check usage limits before transcribing
        val (canTranscribe, errorMessage) = usageRepository.canTranscribeFile(
            audioFile = audioFile,
            isLocal = isLocal,
            isPro = isPro
        )

        if (!canTranscribe && errorMessage != null) {
            return TranscriptionResult.Error(errorMessage)
        }

        // Get the appropriate service
        val service = when (model.provider) {
            ModelProvider.LOCAL -> localService
            ModelProvider.GEMINI -> geminiService
            ModelProvider.OPENAI -> openaiService
        }

        // Perform transcription
        val result = service.transcribe(audioFile, model)

        // Track usage on success
        if (result is TranscriptionResult.Success) {
            val durationMinutes = usageRepository.getAudioDurationMinutes(audioFile)
            usageRepository.trackUsage(isLocal = isLocal, durationMinutes = durationMinutes)
        }

        return result
    }
}
