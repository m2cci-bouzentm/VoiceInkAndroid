package com.voiceink.android.domain.postprocessing

import android.util.Log
import com.voiceink.android.data.preferences.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Types of AI enhancements available
 */
enum class EnhancementType {
    SUMMARIZE,
    CLEAN_UP
}

/**
 * Result of an enhancement operation
 */
sealed class EnhancementResult {
    data class Success(val text: String) : EnhancementResult()
    data class Error(val message: String) : EnhancementResult()
}

/**
 * Service for AI-powered text enhancement using Gemini API
 */
@Singleton
class AIEnhancementService @Inject constructor(
    private val httpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "AIEnhancementService"
        private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Enhance text with the specified enhancement type
     */
    suspend fun enhance(text: String, type: EnhancementType): EnhancementResult {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = settingsRepository.geminiApiKey.first()
                if (apiKey.isBlank()) {
                    return@withContext EnhancementResult.Error("Gemini API key not configured. Add it in Settings.")
                }

                val prompt = buildPrompt(text, type)
                val result = callGeminiApi(apiKey, prompt)

                if (result != null) {
                    EnhancementResult.Success(result)
                } else {
                    EnhancementResult.Error("Failed to enhance text")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Enhancement failed", e)
                EnhancementResult.Error("Enhancement failed: ${e.message}")
            }
        }
    }

    private fun buildPrompt(text: String, type: EnhancementType): String {
        return when (type) {
            EnhancementType.SUMMARIZE -> """
                Summarize the following text in 2-3 concise sentences. Focus on the main points.

                Text: $text

                Summary:
            """.trimIndent()

            EnhancementType.CLEAN_UP -> """
                Clean up and format the following transcribed speech. Fix:
                - Add proper punctuation (periods, commas, question marks)
                - Fix capitalization
                - Remove filler words (um, uh, like)
                - Fix obvious transcription errors
                - Make it read naturally as written text

                Keep the meaning and tone exactly the same. Do not add or remove content.

                Text: $text

                Cleaned up text:
            """.trimIndent()
        }
    }

    private suspend fun callGeminiApi(apiKey: String, prompt: String): String? {
        val requestBody = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt)
                    )
                )
            ),
            generationConfig = GenerationConfig(
                temperature = 0.3f,
                maxOutputTokens = 1024
            )
        )

        val requestJson = json.encodeToString(GeminiRequest.serializer(), requestBody)

        val request = Request.Builder()
            .url("$GEMINI_BASE_URL/gemini-2.5-flash:generateContent?key=$apiKey")
            .post(requestJson.toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            Log.e(TAG, "Gemini API error: ${response.code} - ${response.body?.string()}")
            return null
        }

        val responseBody = response.body?.string() ?: return null
        val geminiResponse = json.decodeFromString(GeminiResponse.serializer(), responseBody)

        return geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
    }

    @Serializable
    private data class GeminiRequest(
        val contents: List<GeminiContent>,
        val generationConfig: GenerationConfig? = null
    )

    @Serializable
    private data class GeminiContent(
        val parts: List<GeminiPart>,
        val role: String? = null
    )

    @Serializable
    private data class GeminiPart(
        val text: String? = null
    )

    @Serializable
    private data class GenerationConfig(
        val temperature: Float? = null,
        val maxOutputTokens: Int? = null
    )

    @Serializable
    private data class GeminiResponse(
        val candidates: List<GeminiCandidate>? = null
    )

    @Serializable
    private data class GeminiCandidate(
        val content: GeminiContent? = null
    )
}
