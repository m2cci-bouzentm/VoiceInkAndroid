package com.voiceink.android.domain.transcription

import android.util.Base64
import com.voiceink.android.data.preferences.SettingsRepository
import com.voiceink.android.domain.model.CloudModel
import com.voiceink.android.domain.model.TranscriptionModel
import com.voiceink.android.domain.model.WhisperLanguages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transcription service using Google Gemini API
 */
@Singleton
class GeminiTranscriptionService @Inject constructor(
    private val httpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository
) : TranscriptionService {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun transcribe(
        audioFile: File,
        model: TranscriptionModel,
        language: String
    ): TranscriptionResult {
        return withContext(Dispatchers.IO) {
            try {
                if (model !is CloudModel) {
                    return@withContext TranscriptionResult.Error("Invalid model type for Gemini")
                }

                val apiKey = settingsRepository.geminiApiKey.first()
                if (apiKey.isBlank()) {
                    return@withContext TranscriptionResult.Error("Gemini API key not configured")
                }

                val audioBytes = audioFile.readBytes()
                val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

                val languageHint = if (language != "auto") {
                    val languageName = WhisperLanguages.findByCode(language)?.name ?: language
                    " The spoken language is $languageName."
                } else {
                    ""
                }

                val requestBody = GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(
                                GeminiPart(
                                    text = "Transcribe this audio accurately.$languageHint Return only the transcription, nothing else."
                                ),
                                GeminiPart(
                                    inlineData = GeminiInlineData(
                                        mimeType = "audio/wav",
                                        data = base64Audio
                                    )
                                )
                            )
                        )
                    )
                )

                val url = "https://generativelanguage.googleapis.com/v1beta/models/${model.modelIdentifier}:generateContent?key=$apiKey"

                val request = Request.Builder()
                    .url(url)
                    .post(json.encodeToString(GeminiRequest.serializer(), requestBody)
                        .toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful) {
                    return@withContext TranscriptionResult.Error(
                        "Gemini API error: ${response.code} - ${responseBody ?: "Unknown error"}"
                    )
                }

                if (responseBody == null) {
                    return@withContext TranscriptionResult.Error("Empty response from Gemini")
                }

                val geminiResponse = json.decodeFromString(GeminiResponse.serializer(), responseBody)
                val transcription = geminiResponse.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text
                    ?: return@withContext TranscriptionResult.Error("No transcription in response")

                TranscriptionResult.Success(transcription.trim())

            } catch (e: Exception) {
                TranscriptionResult.Error("Gemini transcription failed: ${e.message}", e)
            }
        }
    }
}

// Gemini API DTOs
@Serializable
private data class GeminiRequest(
    val contents: List<GeminiContent>
)

@Serializable
private data class GeminiContent(
    val parts: List<GeminiPart>
)

@Serializable
private data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

@Serializable
private data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

@Serializable
private data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

@Serializable
private data class GeminiCandidate(
    val content: GeminiContent? = null
)
