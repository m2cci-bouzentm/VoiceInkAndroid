package com.voiceink.android.domain.transcription

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.voiceink.android.domain.model.LocalModel
import com.voiceink.android.domain.model.TranscriptionModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local transcription using Sherpa-ONNX
 * Supports Parakeet TDT v3 and Whisper models
 */
@Singleton
class LocalTranscriptionService @Inject constructor(
    @ApplicationContext private val context: Context
) : TranscriptionService {

    companion object {
        private const val TAG = "LocalTranscriptionService"
        private const val SAMPLE_RATE = 16000
    }

    private var recognizer: OfflineRecognizer? = null
    private var currentModelId: String? = null

    private var currentLanguage: String? = null

    override suspend fun transcribe(
        audioFile: File,
        model: TranscriptionModel,
        language: String
    ): TranscriptionResult {
        return withContext(Dispatchers.IO) {
            try {
                if (model !is LocalModel) {
                    return@withContext TranscriptionResult.Error("Invalid model type for local transcription")
                }

                if (!audioFile.exists()) {
                    return@withContext TranscriptionResult.Error("Audio file not found")
                }

                // Check if model is marked as broken
                if (model.isBroken) {
                    return@withContext TranscriptionResult.Error(
                        "Model '${model.name}' is currently broken and not supported.\n\n" +
                        "Please select a different model in Settings."
                    )
                }

                // Check if model is available
                if (!isModelAvailable(model)) {
                    return@withContext TranscriptionResult.Error(
                        "Model '${model.name}' is not downloaded.\n\n" +
                        "Go to Settings and tap the download button next to the model."
                    )
                }

                // Determine language to use
                val effectiveLanguage = if (model.supportsLanguageSelection && language != "auto") language else ""
                Log.d(TAG, "Using language: ${if (effectiveLanguage.isEmpty()) "auto-detect" else effectiveLanguage}")

                // Initialize recognizer if needed (also reinit if language changed for Whisper models)
                val needsReinit = recognizer == null ||
                    currentModelId != model.id ||
                    (model.modelType == "whisper" && currentLanguage != effectiveLanguage)

                if (needsReinit) {
                    val initialized = initializeRecognizer(model, effectiveLanguage)
                    if (!initialized) {
                        return@withContext TranscriptionResult.Error("Failed to initialize transcription model")
                    }
                }

                // Read audio samples from WAV file
                val samples = readWavFile(audioFile)
                if (samples.isEmpty()) {
                    return@withContext TranscriptionResult.Error("Failed to read audio file (empty or invalid)")
                }

                val durationSeconds = samples.size / SAMPLE_RATE.toFloat()
                Log.d(TAG, "Transcribing ${samples.size} samples (${String.format("%.2f", durationSeconds)}s of audio)")

                // Check minimum audio length (at least 0.1 seconds)
                if (durationSeconds < 0.1f) {
                    return@withContext TranscriptionResult.Error("Recording too short (${String.format("%.2f", durationSeconds)}s)")
                }

                // Log sample stats for debugging
                val minSample = samples.minOrNull() ?: 0f
                val maxSample = samples.maxOrNull() ?: 0f
                val avgAbsSample = samples.map { kotlin.math.abs(it) }.average()
                Log.d(TAG, "Audio range: min=$minSample, max=$maxSample, avgAbs=$avgAbsSample")

                // Check if audio has actual content (not silence)
                if (maxSample - minSample < 0.01f) {
                    Log.w(TAG, "Audio appears to be silence or very quiet")
                }

                // Create stream and decode
                val currentRecognizer = recognizer
                if (currentRecognizer == null) {
                    return@withContext TranscriptionResult.Error("Recognizer not initialized")
                }

                val stream = currentRecognizer.createStream()
                Log.d(TAG, "Created stream, accepting waveform...")
                stream.acceptWaveform(samples, SAMPLE_RATE)
                Log.d(TAG, "Waveform accepted, decoding...")
                currentRecognizer.decode(stream)
                Log.d(TAG, "Decode complete, getting result...")

                val result = currentRecognizer.getResult(stream)
                stream.release()

                val text = result.text.trim()
                Log.d(TAG, "Transcription result: '$text' (length=${text.length})")

                if (text.isEmpty()) {
                    if (avgAbsSample < 0.01) {
                        TranscriptionResult.Error("No speech detected (audio too quiet)")
                    } else {
                        TranscriptionResult.Error("No speech detected")
                    }
                } else {
                    TranscriptionResult.Success(text)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                TranscriptionResult.Error("Transcription failed: ${e.message}", e)
            }
        }
    }

    /**
     * Initialize the Sherpa-ONNX recognizer for the given model
     * @param model The model to initialize
     * @param language The language code for Whisper models (empty string for auto-detect)
     */
    private fun initializeRecognizer(model: LocalModel, language: String = ""): Boolean {
        return try {
            // Release previous recognizer
            recognizer?.release()
            recognizer = null

            val modelDir = getModelDirectory(model)
            Log.d(TAG, "Initializing model from: ${modelDir.absolutePath}")
            Log.d(TAG, "Model directory exists: ${modelDir.exists()}")
            Log.d(TAG, "Language setting: ${if (language.isEmpty()) "auto-detect" else language}")

            // List files in model directory for debugging
            val files = modelDir.listFiles()
            if (files.isNullOrEmpty()) {
                Log.e(TAG, "Model directory is empty or doesn't exist!")
                return false
            }
            
            files.forEach { file ->
                Log.d(TAG, "  Model file: ${file.name} (${file.length()} bytes)")
            }

            // Verify required files exist with non-zero size
            val requiredFiles = when (model.id) {
                "whisper-tiny-en" -> listOf("tiny.en-encoder.onnx", "tiny.en-decoder.onnx", "tiny.en-tokens.txt")
                "whisper-small" -> listOf("small-encoder.int8.onnx", "small-decoder.int8.onnx", "small-tokens.txt")
                "whisper-medium" -> listOf("medium-encoder.int8.onnx", "medium-decoder.int8.onnx", "medium-tokens.txt")
                "distil-whisper-large-v3" -> listOf("distil-large-v3-encoder.int8.onnx", "distil-large-v3-decoder.int8.onnx", "distil-large-v3-tokens.txt")
                "parakeet-tdt-0.6b" -> listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt")
                else -> emptyList()
            }

            for (fileName in requiredFiles) {
                val file = File(modelDir, fileName)
                if (!file.exists()) {
                    Log.e(TAG, "Required file missing: $fileName")
                    return false
                }
                if (file.length() == 0L) {
                    Log.e(TAG, "Required file is empty (0 bytes): $fileName")
                    return false
                }
                Log.d(TAG, "Verified: $fileName (${file.length()} bytes)")
            }

            val config = when (model.id) {
                "whisper-tiny-en" -> createWhisperTinyEnConfig(modelDir)
                "whisper-small" -> createWhisperSmallConfig(modelDir, language)
                "whisper-medium" -> createWhisperMediumConfig(modelDir, language)
                "distil-whisper-large-v3" -> createDistilWhisperLargeV3Config(modelDir, language)
                "parakeet-tdt-0.6b" -> createParakeetConfig(modelDir)
                else -> {
                    Log.e(TAG, "Unknown model type: ${model.id}")
                    return false
                }
            }

            Log.d(TAG, "Creating OfflineRecognizer...")
            recognizer = OfflineRecognizer(assetManager = null, config = config)
            currentModelId = model.id
            currentLanguage = language

            Log.i(TAG, "Successfully initialized recognizer for model: ${model.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize recognizer: ${e.message}", e)
            recognizer = null
            currentModelId = null
            false
        }
    }

    /**
     * Create config for Whisper Small multilingual model (99+ languages)
     * @param language Language code (empty for auto-detect)
     */
    private fun createWhisperSmallConfig(modelDir: File, language: String = ""): OfflineRecognizerConfig {
        val encoderPath = File(modelDir, "small-encoder.int8.onnx").absolutePath
        val decoderPath = File(modelDir, "small-decoder.int8.onnx").absolutePath
        val tokensPath = File(modelDir, "small-tokens.txt").absolutePath

        Log.d(TAG, "Whisper Small config paths:")
        Log.d(TAG, "  encoder: $encoderPath")
        Log.d(TAG, "  decoder: $decoderPath")
        Log.d(TAG, "  tokens: $tokensPath")
        Log.d(TAG, "  language: ${if (language.isEmpty()) "auto-detect" else language}")

        val whisperConfig = OfflineWhisperModelConfig(
            encoder = encoderPath,
            decoder = decoderPath,
            language = language, // User-selected or empty for auto-detect
            task = "transcribe",
            tailPaddings = 1000
        )

        val modelConfig = OfflineModelConfig(
            whisper = whisperConfig,
            tokens = tokensPath,
            numThreads = 2,
            debug = true,
            modelType = "whisper"
        )

        val featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80)
        Log.d(TAG, "Feature config: sampleRate=$SAMPLE_RATE, featureDim=80 (Whisper Small)")

        return OfflineRecognizerConfig(
            featConfig = featConfig,
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )
    }

    /**
     * Create config for Whisper Tiny English model
     */
    private fun createWhisperTinyEnConfig(modelDir: File): OfflineRecognizerConfig {
        val encoderPath = File(modelDir, "tiny.en-encoder.onnx").absolutePath
        val decoderPath = File(modelDir, "tiny.en-decoder.onnx").absolutePath
        val tokensPath = File(modelDir, "tiny.en-tokens.txt").absolutePath

        Log.d(TAG, "Whisper Tiny EN config paths:")
        Log.d(TAG, "  encoder: $encoderPath")
        Log.d(TAG, "  decoder: $decoderPath")
        Log.d(TAG, "  tokens: $tokensPath")

        val whisperConfig = OfflineWhisperModelConfig(
            encoder = encoderPath,
            decoder = decoderPath,
            language = "en",
            task = "transcribe",
            tailPaddings = 1000
        )

        val modelConfig = OfflineModelConfig(
            whisper = whisperConfig,
            tokens = tokensPath,
            numThreads = 2,
            debug = true,
            modelType = "whisper"
        )

        val featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80)
        Log.d(TAG, "Feature config: sampleRate=$SAMPLE_RATE, featureDim=80 (Whisper Tiny EN)")

        return OfflineRecognizerConfig(
            featConfig = featConfig,
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )
    }

    /**
     * Create config for Whisper Medium multilingual model
     * @param language Language code (empty for auto-detect)
     */
    private fun createWhisperMediumConfig(modelDir: File, language: String = ""): OfflineRecognizerConfig {
        val encoderPath = File(modelDir, "medium-encoder.int8.onnx").absolutePath
        val decoderPath = File(modelDir, "medium-decoder.int8.onnx").absolutePath
        val tokensPath = File(modelDir, "medium-tokens.txt").absolutePath

        Log.d(TAG, "Whisper Medium config paths:")
        Log.d(TAG, "  encoder: $encoderPath")
        Log.d(TAG, "  decoder: $decoderPath")
        Log.d(TAG, "  tokens: $tokensPath")
        Log.d(TAG, "  language: ${if (language.isEmpty()) "auto-detect" else language}")

        val whisperConfig = OfflineWhisperModelConfig(
            encoder = encoderPath,
            decoder = decoderPath,
            language = language, // User-selected or empty for auto-detect
            task = "transcribe",
            tailPaddings = 1000
        )

        val modelConfig = OfflineModelConfig(
            whisper = whisperConfig,
            tokens = tokensPath,
            numThreads = 4, // More threads for larger model
            debug = true,
            modelType = "whisper"
        )

        val featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80)
        Log.d(TAG, "Feature config: sampleRate=$SAMPLE_RATE, featureDim=80 (Whisper Medium)")

        return OfflineRecognizerConfig(
            featConfig = featConfig,
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )
    }

    /**
     * Create config for Whisper Large v3 multilingual model
     * @param language Language code (empty for auto-detect)
     */
    private fun createWhisperLargeV3Config(modelDir: File, language: String = ""): OfflineRecognizerConfig {
        val encoderPath = File(modelDir, "large-v3-encoder.int8.onnx").absolutePath
        val decoderPath = File(modelDir, "large-v3-decoder.int8.onnx").absolutePath
        val tokensPath = File(modelDir, "large-v3-tokens.txt").absolutePath

        Log.d(TAG, "Whisper Large v3 config paths:")
        Log.d(TAG, "  encoder: $encoderPath")
        Log.d(TAG, "  decoder: $decoderPath")
        Log.d(TAG, "  tokens: $tokensPath")
        Log.d(TAG, "  language: ${if (language.isEmpty()) "auto-detect" else language}")

        val whisperConfig = OfflineWhisperModelConfig(
            encoder = encoderPath,
            decoder = decoderPath,
            language = language, // User-selected or empty for auto-detect
            task = "transcribe",
            tailPaddings = 1000
        )

        val modelConfig = OfflineModelConfig(
            whisper = whisperConfig,
            tokens = tokensPath,
            numThreads = 4, // More threads for larger model
            debug = true,
            modelType = "whisper"
        )

        val featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80)
        Log.d(TAG, "Feature config: sampleRate=$SAMPLE_RATE, featureDim=80 (Whisper Large v3)")

        return OfflineRecognizerConfig(
            featConfig = featConfig,
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )
    }

    /**
     * Create config for Distil Whisper Large v3 model
     * @param language Language code (empty for auto-detect)
     */
    private fun createDistilWhisperLargeV3Config(modelDir: File, language: String = ""): OfflineRecognizerConfig {
        val encoderPath = File(modelDir, "distil-large-v3-encoder.int8.onnx").absolutePath
        val decoderPath = File(modelDir, "distil-large-v3-decoder.int8.onnx").absolutePath
        val tokensPath = File(modelDir, "distil-large-v3-tokens.txt").absolutePath

        Log.d(TAG, "Distil Whisper Large v3 config paths:")
        Log.d(TAG, "  encoder: $encoderPath")
        Log.d(TAG, "  decoder: $decoderPath")
        Log.d(TAG, "  tokens: $tokensPath")
        Log.d(TAG, "  language: ${if (language.isEmpty()) "auto-detect" else language}")

        val whisperConfig = OfflineWhisperModelConfig(
            encoder = encoderPath,
            decoder = decoderPath,
            language = language, // User-selected or empty for auto-detect
            task = "transcribe",
            tailPaddings = 1000
        )

        val modelConfig = OfflineModelConfig(
            whisper = whisperConfig,
            tokens = tokensPath,
            numThreads = 4, // More threads for larger model
            debug = true,
            modelType = "whisper"
        )

        val featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80)
        Log.d(TAG, "Feature config: sampleRate=$SAMPLE_RATE, featureDim=80 (Distil Whisper Large v3)")

        return OfflineRecognizerConfig(
            featConfig = featConfig,
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )
    }

    /**
     * Create config for NVIDIA Parakeet TDT 0.6B v3 model (int8 quantized)
     * NOTE: Parakeet v3 uses nemo_transducer model type and feature_dim=80 per sherpa-onnx docs.
     */
    private fun createParakeetConfig(modelDir: File): OfflineRecognizerConfig {
        val encoderPath = File(modelDir, "encoder.int8.onnx").absolutePath
        val decoderPath = File(modelDir, "decoder.int8.onnx").absolutePath
        val joinerPath = File(modelDir, "joiner.int8.onnx").absolutePath
        val tokensPath = File(modelDir, "tokens.txt").absolutePath

        Log.d(TAG, "Parakeet TDT 0.6B v3 config paths:")
        Log.d(TAG, "  encoder: $encoderPath")
        Log.d(TAG, "  decoder: $decoderPath")
        Log.d(TAG, "  joiner: $joinerPath")
        Log.d(TAG, "  tokens: $tokensPath")

        val transducerConfig = OfflineTransducerModelConfig(
            encoder = encoderPath,
            decoder = decoderPath,
            joiner = joinerPath
        )

        val modelConfig = OfflineModelConfig(
            transducer = transducerConfig,
            tokens = tokensPath,
            numThreads = 4, // More threads for larger model
            debug = true,
            modelType = "nemo_transducer"
        )

        // Parakeet v3 uses 80-dimensional mel features (per sherpa-onnx docs)
        val featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80)
        Log.d(TAG, "Feature config: sampleRate=$SAMPLE_RATE, featureDim=80 (Parakeet TDT 0.6B v3)")

        return OfflineRecognizerConfig(
            featConfig = featConfig,
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )
    }

    /**
     * Read WAV file and return float samples
     */
    private fun readWavFile(file: File): FloatArray {
        return try {
            FileInputStream(file).use { fis ->
                val bytes = fis.readBytes()

                // Skip WAV header (44 bytes for standard WAV)
                if (bytes.size <= 44) {
                    return floatArrayOf()
                }

                val audioData = bytes.copyOfRange(44, bytes.size)
                val shortBuffer = ByteBuffer.wrap(audioData)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer()

                val samples = FloatArray(shortBuffer.remaining())
                for (i in samples.indices) {
                    samples[i] = shortBuffer.get() / 32768.0f
                }
                samples
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read WAV file", e)
            floatArrayOf()
        }
    }

    /**
     * Get the model directory in app's files
     */
    private fun getModelDirectory(model: LocalModel): File {
        return File(context.filesDir, "models/${model.modelPath}")
    }

    /**
     * Check if a local model is downloaded and available
     */
    fun isModelAvailable(model: LocalModel): Boolean {
        val modelDir = getModelDirectory(model)
        if (!modelDir.exists()) return false

        // Check for essential files based on model type
        return when (model.id) {
            "whisper-tiny-en" -> {
                File(modelDir, "tiny.en-encoder.onnx").exists() &&
                File(modelDir, "tiny.en-decoder.onnx").exists() &&
                File(modelDir, "tiny.en-tokens.txt").exists()
            }
            "whisper-small" -> {
                File(modelDir, "small-encoder.int8.onnx").exists() &&
                File(modelDir, "small-decoder.int8.onnx").exists() &&
                File(modelDir, "small-tokens.txt").exists()
            }
            "whisper-medium" -> {
                File(modelDir, "medium-encoder.int8.onnx").exists() &&
                File(modelDir, "medium-decoder.int8.onnx").exists() &&
                File(modelDir, "medium-tokens.txt").exists()
            }
            "distil-whisper-large-v3" -> {
                File(modelDir, "distil-large-v3-encoder.int8.onnx").exists() &&
                File(modelDir, "distil-large-v3-decoder.int8.onnx").exists() &&
                File(modelDir, "distil-large-v3-tokens.txt").exists()
            }
            "parakeet-tdt-0.6b" -> {
                File(modelDir, "encoder.int8.onnx").exists() &&
                File(modelDir, "decoder.int8.onnx").exists() &&
                File(modelDir, "joiner.int8.onnx").exists() &&
                File(modelDir, "tokens.txt").exists()
            }
            else -> false
        }
    }

    /**
     * Get the download size for a model
     */
    fun getModelSize(model: LocalModel): Long {
        return when (model.id) {
            "whisper-tiny-en" -> 40_000_000L // ~40MB
            "parakeet-tdt-0.6b" -> 640_000_000L // ~640MB (int8 quantized)
            "whisper-small" -> 460_000_000L // ~460MB
            "distil-whisper-large-v3" -> 1_000_000_000L // ~1GB
            "whisper-medium" -> 1_500_000_000L // ~1.5GB
            else -> 0L
        }
    }

    /**
     * Get the download URL for a model
     */
    fun getModelDownloadUrl(model: LocalModel): String {
        return when (model.id) {
            "whisper-tiny-en" -> "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.en.tar.bz2"
            "parakeet-tdt-0.6b" -> "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2"
            "whisper-small" -> "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-small.tar.bz2"
            "whisper-medium" -> "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-medium.tar.bz2"
            "distil-whisper-large-v3" -> "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-distil-large-v3.tar.bz2"
            else -> ""
        }
    }

    /**
     * Release resources
     */
    fun release() {
        recognizer?.release()
        recognizer = null
        currentModelId = null
    }
}
