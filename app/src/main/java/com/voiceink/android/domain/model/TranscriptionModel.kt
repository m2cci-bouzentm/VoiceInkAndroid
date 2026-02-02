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
 * Badge to highlight model characteristics
 */
enum class ModelBadge {
    FASTEST,        // Quickest transcription
    RECOMMENDED,    // Best balance of speed/accuracy
    MOST_ACCURATE,  // Highest accuracy
    ENGLISH_BEST,   // Best for English specifically
    NONE            // No special badge
}

/**
 * Base interface for all transcription models
 */
sealed interface TranscriptionModel {
    val id: String
    val name: String
    val description: String
    val provider: ModelProvider
    val badge: ModelBadge
    val benchmark: ModelBenchmark?
}

/**
 * Benchmark metadata for model selection UI.
 *
 * Values are sourced from official model cards/papers and should not be guessed.
 * If no trustworthy public benchmark exists, leave fields null.
 */
data class ModelBenchmark(
    val wer: Double? = null,
    val werDataset: String? = null,
    val paramsM: Int? = null,
    val rtfx: Double? = null,
    val relativeLatency: Double? = null
)

/**
 * Converts benchmark metrics into coarse 1–5 UI scores.
 * This is a deliberately coarse binning to avoid false precision.
 */
object ModelScoring {
    fun accuracyScore(benchmark: ModelBenchmark?): Int? {
        val wer = benchmark?.wer ?: return null
        return when {
            wer <= 2.0 -> 5
            wer <= 3.0 -> 4
            wer <= 4.5 -> 3
            wer <= 7.0 -> 2
            else -> 1
        }
    }

    fun speedScore(benchmark: ModelBenchmark?): Int? {
        benchmark?.rtfx?.let { rtfx ->
            return when {
                rtfx >= 1000 -> 5
                rtfx >= 100 -> 4
                rtfx >= 10 -> 3
                rtfx >= 1 -> 2
                else -> 1
            }
        }

        benchmark?.relativeLatency?.let { rel ->
            return when {
                rel >= 6.0 -> 5
                rel >= 4.0 -> 4
                rel >= 2.0 -> 3
                rel >= 1.0 -> 2
                else -> 1
            }
        }

        benchmark?.paramsM?.let { params ->
            return when {
                params <= 50 -> 5
                params <= 250 -> 4
                params <= 800 -> 3
                params <= 1200 -> 2
                else -> 1
            }
        }

        return null
    }
}

/**
 * Local model using Sherpa-ONNX
 */
data class LocalModel(
    override val id: String,
    override val name: String,
    override val description: String,
    override val badge: ModelBadge = ModelBadge.NONE,
    override val benchmark: ModelBenchmark? = null,
    val modelPath: String,
    val language: String = "en",
    val isBroken: Boolean = false,
    val modelType: String = "transducer",
    val downloadSizeMB: Int = 0,
    val supportsLanguageSelection: Boolean = false
) : TranscriptionModel {
    override val provider = ModelProvider.LOCAL
}

/**
 * Cloud model using REST API
 */
data class CloudModel(
    override val id: String,
    override val name: String,
    override val description: String,
    override val badge: ModelBadge = ModelBadge.NONE,
    override val benchmark: ModelBenchmark? = null,
    override val provider: ModelProvider,
    val modelIdentifier: String
) : TranscriptionModel

/**
 * Predefined models available in the app
 * 
 * BENCHMARK SOURCES (trustworthy, do not guess numbers):
 * - Whisper Tiny EN WER (LibriSpeech test-clean): https://huggingface.co/openai/whisper-tiny.en
 * - Whisper Small WER (LibriSpeech test-clean): https://huggingface.co/openai/whisper-small
 * - Whisper Medium WER (LibriSpeech test-clean): https://huggingface.co/openai/whisper-medium
 * - Whisper params table (all Whisper sizes): https://huggingface.co/openai/whisper
 * - Distil Whisper Large v3 WER (LibriSpeech validation-clean) + params + rel. latency:
 *   https://huggingface.co/distil-whisper/distil-large-v3
 * - Parakeet TDT 0.6B WER (LibriSpeech test-clean) + params + RTFx:
 *   https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2
 *
 * UI uses coarse 1–5 bins (see ModelScoring) derived from these metrics.
 */
object PredefinedModels {
    
    // ==================== LOCAL MODELS (On-Device, Private) ====================
    
    // Whisper Tiny - Fastest, English only
    // Benchmark: WER 5.66% LibriSpeech clean (HuggingFace)
    val whisperTinyEn = LocalModel(
        id = "whisper-tiny-en",
        name = "Whisper Tiny",
        description = "English only, 39M params",
        badge = ModelBadge.FASTEST,
        benchmark = ModelBenchmark(
            wer = 5.655609406528749,
            werDataset = "LibriSpeech test-clean",
            paramsM = 39
        ),
        modelPath = "whisper-tiny-en",
        language = "en",
        modelType = "whisper",
        downloadSizeMB = 40
    )
    
    // Parakeet TDT 0.6B - Best for English
    // Benchmark: WER 1.69% LibriSpeech clean, RTFx 3380 (NVIDIA HuggingFace)
    // BROKEN: sherpa-onnx v3 export missing vocab_size metadata in decoder.int8.onnx
    // Issue: https://github.com/k2-fsa/sherpa-onnx/issues - runtime reads vocab_size from decoder, 
    // but export script (PR #2500) only added it to encoder. Causes SIGABRT crash.
    val parakeetTdt = LocalModel(
        id = "parakeet-tdt-0.6b",
        name = "Parakeet TDT 0.6B",
        description = "NVIDIA, WER 1.69%",
        badge = ModelBadge.ENGLISH_BEST,
        benchmark = ModelBenchmark(
            wer = 1.69,
            werDataset = "LibriSpeech test-clean",
            paramsM = 600,
            rtfx = 3380.0
        ),
        modelPath = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
        language = "en",
        modelType = "transducer",
        downloadSizeMB = 490,
        isBroken = true  // Missing decoder metadata, crashes on Android
    )
    
    // Whisper Small - Multilingual, smaller download
    // Benchmark: WER 3.43% LibriSpeech clean (OpenAI/HuggingFace)
    val whisperSmall = LocalModel(
        id = "whisper-small",
        name = "Whisper Small",
        description = "99+ languages, 244M params",
        badge = ModelBadge.NONE,
        benchmark = ModelBenchmark(
            wer = 3.432213777886737,
            werDataset = "LibriSpeech test-clean",
            paramsM = 244
        ),
        modelPath = "sherpa-onnx-whisper-small",
        language = "auto",
        modelType = "whisper",
        downloadSizeMB = 460,
        supportsLanguageSelection = true
    )
    
    // Distil Whisper Large v3 - Fast & accurate multilingual
    // Benchmark: WER 2.43% LibriSpeech clean, 6.3x faster than large-v3 (HuggingFace)
    val distilWhisperLargeV3 = LocalModel(
        id = "distil-whisper-large-v3",
        name = "Distil Whisper Large v3",
        description = "99+ langs, 6.3x faster",
        badge = ModelBadge.RECOMMENDED,
        benchmark = ModelBenchmark(
            wer = 2.428920763531516,
            werDataset = "LibriSpeech validation-clean",
            paramsM = 756,
            relativeLatency = 6.3
        ),
        modelPath = "sherpa-onnx-whisper-distil-large-v3",
        language = "auto",
        modelType = "whisper",
        downloadSizeMB = 1000,
        supportsLanguageSelection = true
    )
    
    // Whisper Medium - High accuracy multilingual
    // Benchmark: WER 2.90% LibriSpeech clean (OpenAI/HuggingFace)
    val whisperMedium = LocalModel(
        id = "whisper-medium",
        name = "Whisper Medium",
        description = "99+ languages, 769M params",
        badge = ModelBadge.MOST_ACCURATE,
        benchmark = ModelBenchmark(
            wer = 2.900409225488902,
            werDataset = "LibriSpeech test-clean",
            paramsM = 769
        ),
        modelPath = "sherpa-onnx-whisper-medium",
        language = "auto",
        modelType = "whisper",
        downloadSizeMB = 1500,
        supportsLanguageSelection = true
    )
    
    // ==================== CLOUD MODELS (Requires Internet + API Key) ====================
    // Note: Cloud model benchmarks vary by audio quality/length
    
    val gemini25Flash = CloudModel(
        id = "gemini-2.5-flash",
        name = "Gemini 2.5 Flash",
        description = "Google AI, latest model",
        badge = ModelBadge.FASTEST,
        provider = ModelProvider.GEMINI,
        modelIdentifier = "gemini-2.5-flash"
    )
    
    val openaiWhisper = CloudModel(
        id = "openai-whisper",
        name = "OpenAI Whisper",
        description = "Industry standard API",
        badge = ModelBadge.MOST_ACCURATE,
        provider = ModelProvider.OPENAI,
        modelIdentifier = "whisper-1"
    )
    
    // Gemini 2.0 (older version, still functional)
    val gemini20Flash = CloudModel(
        id = "gemini-2.0-flash",
        name = "Gemini 2.0 Flash",
        description = "Google AI, stable version",
        badge = ModelBadge.NONE,
        provider = ModelProvider.GEMINI,
        modelIdentifier = "gemini-2.0-flash"
    )

    // ==================== MODEL LISTS ====================
    
    // Main models shown to users (curated, clear choices)
    val featuredModels: List<TranscriptionModel> = listOf(
        // Local - ordered by size/use case
        whisperTinyEn,          // Fastest, English
        parakeetTdt,            // Best for English (NVIDIA)
        whisperSmall,           // Multilingual, smaller
        distilWhisperLargeV3,   // Recommended, multilingual
        whisperMedium,          // Most accurate, multilingual
        // Cloud
        gemini25Flash,          // Latest Gemini
        gemini20Flash,          // Stable Gemini
        openaiWhisper           // Accurate cloud
    )
    
    // All models
    val allModels: List<TranscriptionModel> = listOf(
        whisperTinyEn,
        parakeetTdt,
        whisperSmall,
        distilWhisperLargeV3,
        whisperMedium,
        gemini25Flash,
        gemini20Flash,
        openaiWhisper
    )

    val cloudModels: List<CloudModel> = listOf(
        gemini25Flash,
        openaiWhisper,
        gemini20Flash
    )

    val localModels: List<LocalModel> = listOf(
        whisperTinyEn,
        parakeetTdt,
        whisperSmall,
        distilWhisperLargeV3,
        whisperMedium
    )
}
