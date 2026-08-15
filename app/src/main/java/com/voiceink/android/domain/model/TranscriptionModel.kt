package com.voiceink.android.domain.model

/**
 * Represents a transcription model provider
 */
enum class ModelProvider {
    LOCAL,       // Sherpa-ONNX local models
    GEMINI,      // Google Gemini API
    OPENAI,      // OpenAI Whisper API
    OPENROUTER   // OpenRouter — any audio-capable model, named by the user
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
    val relativeLatency: Double? = null,
    val avgSecPerFile: Double? = null,
    val tokensPerSecond: Double? = null,
    val ttftMs: Double? = null
)

/**
 * Converts benchmark metrics into coarse 1–5 UI scores.
 * This is a deliberately coarse binning to avoid false precision.
 */
object ModelScoring {
    /**
     * WER is only meaningful against the benchmark it was measured on: 12% on
     * PriMock57's accented medical speech is a better result than 6% on
     * LibriSpeech's clean read audio. Binning every number on one scale made
     * cloud models look broken, so each benchmark carries its own thresholds,
     * ordered best-to-worst. A score therefore reads "how good for this
     * benchmark", never "better than the model above it".
     */
    private val werThresholds: Map<String, List<Double>> = mapOf(
        "LibriSpeech" to listOf(2.0, 3.0, 4.5, 7.0),
        "Open-ASR avg" to listOf(6.0, 7.5, 9.0, 12.0),
        "short-form" to listOf(8.0, 10.0, 12.0, 15.0),
        "PriMock57 medical" to listOf(10.0, 13.0, 16.0, 20.0)
    )

    fun accuracyScore(benchmark: ModelBenchmark?): Int? {
        val wer = benchmark?.wer ?: return null
        val thresholds = werThresholds[benchmark.werDataset] ?: return null
        return 5 - thresholds.count { wer > it }
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

        benchmark?.avgSecPerFile?.let { seconds ->
            return when {
                seconds <= 5.0 -> 5
                seconds <= 10.0 -> 4
                seconds <= 20.0 -> 3
                seconds <= 40.0 -> 2
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

        benchmark?.tokensPerSecond?.let { tps ->
            return when {
                tps >= 200 -> 5
                tps >= 100 -> 4
                tps >= 50 -> 3
                tps >= 20 -> 2
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
 * - Distil Whisper Large v3 WER + params + rel. latency:
 *   https://github.com/huggingface/distil-whisper
 * - Parakeet v3 Open-ASR avg WER:
 *   https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3
 * - Parakeet v3 + cloud models (Gemini 2.5 Flash, OpenAI Whisper-1) long-form medical benchmark:
 *   https://omi.health/benchmarking-tts
 * - Gemini 2.0 Flash API throughput/TTFT:
 *   https://llm-benchmarks.com/models/vertex/gemini20flashexp
 * - Parakeet v3 model details (size/languages) + file sizes:
 *   https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/nemo-transducer-models.html
 *
 * UI uses coarse 1–5 bins (see ModelScoring) derived from these metrics.
 */
object PredefinedModels {
    
    // ==================== LOCAL MODELS (On-Device, Private) ====================
    
    // Whisper Tiny - Fastest, English only
    // Benchmark: WER 5.6556% LibriSpeech test-clean (HuggingFace)
    val whisperTinyEn = LocalModel(
        id = "whisper-tiny-en",
        name = "Whisper Tiny",
        description = "English only, 39M params",
        badge = ModelBadge.FASTEST,
        benchmark = ModelBenchmark(
            wer = 5.6556,
            werDataset = "LibriSpeech",
            paramsM = 39
        ),
        modelPath = "whisper-tiny-en",
        language = "en",
        modelType = "whisper",
        downloadSizeMB = 40
    )
    
    // Parakeet TDT 0.6B v3 - 25 European languages
    // Accuracy: Open-ASR leaderboard avg (HuggingFace model card)
    // Speed: Omi Voice LLM Benchmark (PriMock57 medical set)
    // Model details + file sizes from sherpa-onnx docs
    val parakeetTdt = LocalModel(
        id = "parakeet-tdt-0.6b",
        name = "Parakeet TDT 0.6B",
        description = "NVIDIA, 25 languages",
        badge = ModelBadge.NONE,
        benchmark = ModelBenchmark(
            wer = 6.34,
            werDataset = "Open-ASR avg",
            paramsM = 600,
            avgSecPerFile = 6.0
        ),
        modelPath = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
        language = "auto",
        modelType = "transducer",
        downloadSizeMB = 640
    )
    
    // Whisper Small - Multilingual, smaller download
    // Benchmark: WER 3.432% LibriSpeech test-clean (OpenAI/HuggingFace)
    val whisperSmall = LocalModel(
        id = "whisper-small",
        name = "Whisper Small",
        description = "99+ languages, 244M params",
        badge = ModelBadge.NONE,
        benchmark = ModelBenchmark(
            wer = 3.432,
            werDataset = "LibriSpeech",
            paramsM = 244
        ),
        modelPath = "sherpa-onnx-whisper-small",
        language = "auto",
        modelType = "whisper",
        downloadSizeMB = 460,
        supportsLanguageSelection = true
    )
    
    // Distil Whisper Large v3 - Fast & accurate English
    // Benchmark: Short-form WER 9.7, 6.3x rel. latency (Distil-Whisper model card)
    val distilWhisperLargeV3 = LocalModel(
        id = "distil-whisper-large-v3",
        name = "Distil Whisper Large v3",
        description = "99+ langs, 6.3x faster",
        badge = ModelBadge.NONE,
        benchmark = ModelBenchmark(
            wer = 9.7,
            werDataset = "short-form",
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
            werDataset = "LibriSpeech",
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
        badge = ModelBadge.NONE,
        benchmark = ModelBenchmark(
            wer = 12.1,
            werDataset = "PriMock57 medical",
            avgSecPerFile = 20.0
        ),
        provider = ModelProvider.GEMINI,
        modelIdentifier = "gemini-2.5-flash"
    )
    
    val openaiWhisper = CloudModel(
        id = "openai-whisper",
        name = "OpenAI Whisper",
        description = "Industry standard API",
        badge = ModelBadge.NONE,
        benchmark = ModelBenchmark(
            wer = 15.5,
            werDataset = "PriMock57 medical",
            avgSecPerFile = 104.0
        ),
        provider = ModelProvider.OPENAI,
        modelIdentifier = "whisper-1"
    )
    
    // Gemini 2.0 (older version, still functional)
    val gemini20Flash = CloudModel(
        id = "gemini-2.0-flash",
        name = "Gemini 2.0 Flash",
        description = "Google AI, stable version",
        badge = ModelBadge.NONE,
        benchmark = ModelBenchmark(
            tokensPerSecond = 61.7,
            ttftMs = 640.0
        ),
        provider = ModelProvider.GEMINI,
        modelIdentifier = "gemini-2.0-flash"
    )

    /**
     * OpenRouter routes to hundreds of models and the catalogue moves weekly, so
     * shipping a hardcoded list would be stale immediately and mostly noise. The
     * user types the model slug themselves; `modelIdentifier` is only a
     * placeholder, and the real value is read from settings at call time.
     */
    val openRouterCustom = CloudModel(
        id = "openrouter-custom",
        name = "OpenRouter",
        description = "Any audio-capable model — you name it",
        badge = ModelBadge.NONE,
        benchmark = null,
        provider = ModelProvider.OPENROUTER,
        modelIdentifier = ""
    )

    // ==================== MODEL LISTS ====================
    private val localModelList: List<LocalModel> = listOf(
        whisperTinyEn,
        parakeetTdt,
        whisperSmall,
        distilWhisperLargeV3,
        whisperMedium
    )

    private val cloudModelList: List<CloudModel> = listOf(
        gemini25Flash,
        gemini20Flash,
        openaiWhisper,
        openRouterCustom
    )

    // Main models shown to users (curated, clear choices)
    val featuredModels: List<TranscriptionModel> = localModelList + cloudModelList

    // All models
    val allModels: List<TranscriptionModel> = featuredModels

    val cloudModels: List<CloudModel> = cloudModelList

    val localModels: List<LocalModel> = localModelList
}
