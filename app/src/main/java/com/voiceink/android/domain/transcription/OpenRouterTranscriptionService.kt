package com.voiceink.android.domain.transcription

import android.util.Base64
import com.voiceink.android.data.preferences.SettingsRepository
import com.voiceink.android.domain.model.CloudModel
import com.voiceink.android.domain.model.TranscriptionModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transcription via OpenRouter.
 *
 * Unlike the other cloud providers this has no fixed model: OpenRouter fronts
 * hundreds of them and the list changes constantly, so the user types the slug
 * (e.g. "google/gemini-2.5-flash") and it is read from settings here rather
 * than baked into the model definition.
 *
 * The API is OpenAI-compatible chat completions, so the audio goes in as an
 * `input_audio` content part and the transcript comes back as message content —
 * there is no dedicated transcription endpoint. That means the model must
 * accept audio input; a text-only slug will fail at the API with a clear error.
 */
@Singleton
class OpenRouterTranscriptionService @Inject constructor(
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
                    return@withContext TranscriptionResult.Error("Invalid model type for OpenRouter")
                }

                val apiKey = settingsRepository.openRouterApiKey.first()
                if (apiKey.isBlank()) {
                    return@withContext TranscriptionResult.Error("OpenRouter API key not configured")
                }

                val modelId = settingsRepository.openRouterModelId.first().trim()
                if (modelId.isBlank()) {
                    return@withContext TranscriptionResult.Error(
                        "No OpenRouter model set. Enter a model ID in Settings, " +
                            "for example google/gemini-2.5-flash"
                    )
                }

                val audioBase64 = Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP)

                val instruction = buildString {
                    append("Transcribe this audio verbatim. ")
                    append("Reply with the transcription only — no preamble, no commentary, ")
                    append("no quotes around it. ")
                    if (language != "auto") {
                        append("The audio is in language code '$language'. ")
                    }
                }

                val payload = buildJsonObject {
                    put("model", modelId)
                    putJsonArray("messages") {
                        add(buildJsonObject {
                            put("role", "user")
                            putJsonArray("content") {
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", instruction)
                                })
                                add(buildJsonObject {
                                    put("type", "input_audio")
                                    putJsonObject("input_audio") {
                                        put("data", audioBase64)
                                        put("format", "wav")
                                    }
                                })
                            }
                        })
                    }
                }

                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    // OpenRouter uses these for attribution on its dashboard.
                    .header("HTTP-Referer", "https://github.com/m2cci-bouzentm/VoiceInkAndroid")
                    .header("X-Title", "VoiceInk Android")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful) {
                    return@withContext TranscriptionResult.Error(
                        "OpenRouter API error: ${response.code} - ${responseBody ?: "Unknown error"}"
                    )
                }

                if (responseBody == null) {
                    return@withContext TranscriptionResult.Error("Empty response from OpenRouter")
                }

                val parsed = json.decodeFromString(OpenRouterResponse.serializer(), responseBody)
                parsed.error?.message?.let {
                    return@withContext TranscriptionResult.Error("OpenRouter: $it")
                }

                val text = parsed.choices?.firstOrNull()?.message?.content?.trim()
                if (text.isNullOrBlank()) {
                    return@withContext TranscriptionResult.Error(
                        "No transcription in response — does '$modelId' accept audio input?"
                    )
                }

                TranscriptionResult.Success(text)

            } catch (e: Exception) {
                TranscriptionResult.Error("OpenRouter transcription failed: ${e.message}", e)
            }
        }
    }
}

@Serializable
private data class OpenRouterResponse(
    val choices: List<OpenRouterChoice>? = null,
    val error: OpenRouterError? = null
)

@Serializable
private data class OpenRouterChoice(
    val message: OpenRouterMessage? = null
)

@Serializable
private data class OpenRouterMessage(
    val content: String? = null
)

@Serializable
private data class OpenRouterError(
    val message: String? = null
)
