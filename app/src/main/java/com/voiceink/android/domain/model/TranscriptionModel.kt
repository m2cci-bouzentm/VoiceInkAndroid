package com.voiceink.android.domain.model

/**
 * Represents a transcription model provider
 */
enum class ModelProvider {
    LOCAL,      // Sherpa-ONNX local models
    GEMINI,     // Google Gemini API
    OPENAI      // OpenAI Whisper API
}

/**
 * Base interface for all transcription models
 */
sealed interface TranscriptionModel {
    val id: String
    val name: String
    val provider: ModelProvider
}

/**
 * Local model using Sherpa-ONNX
 */
data class LocalModel(
    override val id: String,
    override val name: String,
    val modelPath: String,
    val language: String = "en",
    val isBroken: Boolean = false, // Mark models that don't work
    val modelType: String = "transducer" // transducer, whisper, sense_voice
) : TranscriptionModel {
    override val provider = ModelProvider.LOCAL
}

/**
 * Cloud model using REST API
 */
data class CloudModel(
    override val id: String,
    override val name: String,
    override val provider: ModelProvider,
    val modelIdentifier: String
) : TranscriptionModel

/**
 * Predefined models available in the app
 */
object PredefinedModels {
    // Local models (Sherpa-ONNX)
    
    // Whisper Small - 99+ languages support (RECOMMENDED for multilingual)
    val whisperSmall = LocalModel(
        id = "whisper-small",
        name = "Whisper Small (99+ Languages)",
        modelPath = "sherpa-onnx-whisper-small",
        language = "auto",
        modelType = "whisper"
    )
    
    // Whisper Tiny English - Fast, English only
    val whisperTinyEn = LocalModel(
        id = "whisper-tiny-en",
        name = "Whisper Tiny (English)",
        modelPath = "whisper-tiny-en",
        language = "en",
        modelType = "whisper"
    )

    // Gemini models
    val gemini25Flash = CloudModel(
        id = "gemini-2.5-flash",
        name = "Gemini 2.5 Flash",
        provider = ModelProvider.GEMINI,
        modelIdentifier = "gemini-2.5-flash"
    )

    val gemini20Flash = CloudModel(
        id = "gemini-2.0-flash",
        name = "Gemini 2.0 Flash",
        provider = ModelProvider.GEMINI,
        modelIdentifier = "gemini-2.0-flash"
    )

    // OpenAI models
    val openaiWhisper = CloudModel(
        id = "openai-whisper",
        name = "OpenAI Whisper",
        provider = ModelProvider.OPENAI,
        modelIdentifier = "whisper-1"
    )

    val allModels: List<TranscriptionModel> = listOf(
        whisperSmall,
        whisperTinyEn,
        gemini25Flash,
        gemini20Flash,
        openaiWhisper
    )

    val cloudModels: List<CloudModel> = listOf(
        gemini25Flash,
        gemini20Flash,
        openaiWhisper
    )

    val localModels: List<LocalModel> = listOf(
        whisperSmall,
        whisperTinyEn
    )
}
