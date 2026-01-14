package com.voiceink.android.domain.transcription

import com.voiceink.android.data.preferences.SettingsRepository
import com.voiceink.android.domain.model.CloudModel
import com.voiceink.android.domain.model.TranscriptionModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transcription service using OpenAI Whisper API
 */
@Singleton
class OpenAITranscriptionService @Inject constructor(
    private val httpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository
) : TranscriptionService {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun transcribe(audioFile: File, model: TranscriptionModel): TranscriptionResult {
        return withContext(Dispatchers.IO) {
            try {
                if (model !is CloudModel) {
                    return@withContext TranscriptionResult.Error("Invalid model type for OpenAI")
                }

                val apiKey = settingsRepository.openaiApiKey.first()
                if (apiKey.isBlank()) {
                    return@withContext TranscriptionResult.Error("OpenAI API key not configured")
                }

                // OpenAI Whisper API uses multipart/form-data
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        audioFile.name,
                        audioFile.asRequestBody("audio/wav".toMediaType())
                    )
                    .addFormDataPart("model", model.modelIdentifier)
                    .addFormDataPart("response_format", "json")
                    .build()

                val request = Request.Builder()
                    .url("https://api.openai.com/v1/audio/transcriptions")
                    .header("Authorization", "Bearer $apiKey")
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful) {
                    return@withContext TranscriptionResult.Error(
                        "OpenAI API error: ${response.code} - ${responseBody ?: "Unknown error"}"
                    )
                }

                if (responseBody == null) {
                    return@withContext TranscriptionResult.Error("Empty response from OpenAI")
                }

                val whisperResponse = json.decodeFromString(WhisperResponse.serializer(), responseBody)
                val transcription = whisperResponse.text
                    ?: return@withContext TranscriptionResult.Error("No transcription in response")

                TranscriptionResult.Success(transcription.trim())

            } catch (e: Exception) {
                TranscriptionResult.Error("OpenAI transcription failed: ${e.message}", e)
            }
        }
    }
}

// OpenAI Whisper API Response
@Serializable
private data class WhisperResponse(
    val text: String? = null
)
