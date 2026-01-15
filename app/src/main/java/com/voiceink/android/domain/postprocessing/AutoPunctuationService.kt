package com.voiceink.android.domain.postprocessing

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
 * Service that adds proper punctuation and capitalization to transcribed text using Gemini.
 *
 * This is used as a post-processing step after local model transcription,
 * which typically produces unpunctuated text.
 */
@Singleton
class AutoPunctuationService @Inject constructor(
    private val httpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Add punctuation and proper capitalization to text
     *
     * @param rawText The unpunctuated text from transcription
     * @return The punctuated text, or the original text if punctuation fails
     */
    suspend fun punctuate(rawText: String): String {
        if (rawText.isBlank()) return rawText

        return withContext(Dispatchers.IO) {
            try {
                val apiKey = settingsRepository.geminiApiKey.first()
                if (apiKey.isBlank()) {
                    // No API key, return original text
                    return@withContext rawText
                }

                val prompt = """
                    Add proper punctuation and capitalization to this transcribed speech.
                    Do NOT change any words, only add punctuation marks and fix capitalization.
                    Return ONLY the punctuated text, nothing else.

                    Text: $rawText
                """.trimIndent()

                val requestBody = PunctuationRequest(
                    contents = listOf(
                        PunctuationContent(
                            parts = listOf(
                                PunctuationPart(text = prompt)
                            )
                        )
                    )
                )

                // Use gemini-2.0-flash for fast punctuation
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"

                val request = Request.Builder()
                    .url(url)
                    .post(
                        json.encodeToString(PunctuationRequest.serializer(), requestBody)
                            .toRequestBody("application/json".toMediaType())
                    )
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody == null) {
                    // API error, return original text
                    return@withContext rawText
                }

                val geminiResponse = json.decodeFromString(PunctuationResponse.serializer(), responseBody)
                val punctuatedText = geminiResponse.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text

                punctuatedText?.trim() ?: rawText

            } catch (e: Exception) {
                // Any error, return original text
                rawText
            }
        }
    }

    /**
     * Check if auto-punctuation is available (requires Gemini API key)
     */
    suspend fun isAvailable(): Boolean {
        return settingsRepository.geminiApiKey.first().isNotBlank()
    }
}

// Gemini API DTOs for punctuation
@Serializable
private data class PunctuationRequest(
    val contents: List<PunctuationContent>
)

@Serializable
private data class PunctuationContent(
    val parts: List<PunctuationPart>
)

@Serializable
private data class PunctuationPart(
    val text: String
)

@Serializable
private data class PunctuationResponse(
    val candidates: List<PunctuationCandidate>? = null
)

@Serializable
private data class PunctuationCandidate(
    val content: PunctuationContent? = null
)
