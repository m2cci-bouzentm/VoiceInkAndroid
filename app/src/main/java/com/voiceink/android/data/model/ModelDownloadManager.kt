package com.voiceink.android.data.model

import android.content.Context
import android.util.Log
import com.voiceink.android.domain.model.LocalModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Download state for a model
 */
sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState() // 0-100
    object Extracting : DownloadState()
    object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * Manages downloading and extracting local transcription models
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val BUFFER_SIZE = 8192
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    /**
     * Get download state for a specific model
     */
    fun getDownloadState(modelId: String): DownloadState {
        return _downloadStates.value[modelId] ?: DownloadState.Idle
    }

    /**
     * Check if a model is downloaded (LocalModel)
     */
    fun isModelDownloaded(model: LocalModel): Boolean {
        val modelDir = getModelDirectory(model)
        if (!modelDir.exists()) return false

        return when (model.id) {
            "whisper-small" -> {
                // Whisper Small multilingual model files
                File(modelDir, "small-encoder.int8.onnx").exists() &&
                File(modelDir, "small-decoder.int8.onnx").exists() &&
                File(modelDir, "small-tokens.txt").exists()
            }
            "whisper-tiny-en" -> {
                // Whisper tiny.en model files
                File(modelDir, "tiny.en-encoder.onnx").exists() &&
                File(modelDir, "tiny.en-decoder.onnx").exists() &&
                File(modelDir, "tiny.en-tokens.txt").exists()
            }
            else -> false
        }
    }

    /**
     * Download and extract a model
     */
    suspend fun downloadModel(model: LocalModel): Result<Unit> = withContext(Dispatchers.IO) {
        val modelId = model.id

        try {
            updateState(modelId, DownloadState.Downloading(0))

            val downloadUrl = getDownloadUrl(model)
            if (downloadUrl.isEmpty()) {
                updateState(modelId, DownloadState.Error("No download URL for this model"))
                return@withContext Result.failure(Exception("No download URL"))
            }

            Log.d(TAG, "Downloading model from: $downloadUrl")

            // Create temp file for download
            val tempFile = File(context.cacheDir, "${modelId}_temp.tar.bz2")

            // Download file
            val request = Request.Builder().url(downloadUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                updateState(modelId, DownloadState.Error("Download failed: ${response.code}"))
                return@withContext Result.failure(Exception("Download failed"))
            }

            val body = response.body ?: run {
                updateState(modelId, DownloadState.Error("Empty response"))
                return@withContext Result.failure(Exception("Empty response"))
            }

            val contentLength = body.contentLength()
            var downloadedBytes = 0L

            // Write to temp file with progress
            FileOutputStream(tempFile).use { fos ->
                body.byteStream().use { inputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (contentLength > 0) {
                            val progress = ((downloadedBytes * 100) / contentLength).toInt()
                            updateState(modelId, DownloadState.Downloading(progress))
                        }
                    }
                }
            }

            Log.d(TAG, "Download complete, extracting...")
            updateState(modelId, DownloadState.Extracting)

            // Extract tar.bz2
            val modelDir = getModelDirectory(model)
            modelDir.mkdirs()

            extractTarBz2(tempFile, modelDir)

            // Clean up temp file
            tempFile.delete()

            // Move files from nested directory if needed
            flattenModelDirectory(modelDir)

            Log.d(TAG, "Model extraction complete")
            updateState(modelId, DownloadState.Completed)

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            updateState(modelId, DownloadState.Error(e.message ?: "Unknown error"))
            Result.failure(e)
        }
    }

    /**
     * Extract tar.bz2 archive
     */
    private fun extractTarBz2(archiveFile: File, destDir: File) {
        BufferedInputStream(archiveFile.inputStream()).use { bis ->
            BZip2CompressorInputStream(bis).use { bzis ->
                TarArchiveInputStream(bzis).use { tais ->
                    var entry = tais.nextTarEntry
                    while (entry != null) {
                        val outputFile = File(destDir, entry.name)

                        if (entry.isDirectory) {
                            outputFile.mkdirs()
                        } else {
                            outputFile.parentFile?.mkdirs()
                            FileOutputStream(outputFile).use { fos ->
                                tais.copyTo(fos)
                            }
                        }

                        entry = tais.nextTarEntry
                    }
                }
            }
        }
    }

    /**
     * Flatten model directory - move files from nested subdirectory to main directory
     */
    private fun flattenModelDirectory(modelDir: File) {
        val subdirs = modelDir.listFiles { file -> file.isDirectory }

        subdirs?.forEach { subdir ->
            // Move all files from subdirectory to parent
            subdir.listFiles()?.forEach { file ->
                val destFile = File(modelDir, file.name)
                if (!destFile.exists()) {
                    file.renameTo(destFile)
                }
            }
            // Delete empty subdirectory
            subdir.deleteRecursively()
        }
    }

    /**
     * Delete a downloaded model
     */
    suspend fun deleteModel(model: LocalModel) = withContext(Dispatchers.IO) {
        val modelDir = getModelDirectory(model)
        if (modelDir.exists()) {
            modelDir.deleteRecursively()
        }
        updateState(model.id, DownloadState.Idle)
    }

    /**
     * Get model directory
     */
    fun getModelDirectory(model: LocalModel): File {
        return File(context.filesDir, "models/${model.modelPath}")
    }

    /**
     * Get download URL for a model
     */
    private fun getDownloadUrl(model: LocalModel): String {
        return when (model.id) {
            "whisper-small" -> "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-small.tar.bz2"
            "whisper-tiny-en" -> "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.en.tar.bz2"
            else -> ""
        }
    }

    /**
     * Get model size in bytes (approximate download size)
     */
    fun getModelSize(model: LocalModel): Long {
        return when (model.id) {
            "whisper-small" -> 640_000_000L // ~640MB
            "whisper-tiny-en" -> 40_000_000L // ~40MB
            else -> 0L
        }
    }

    /**
     * Get human-readable model size
     */
    fun getModelSizeString(model: LocalModel): String {
        val sizeBytes = getModelSize(model)
        return when {
            sizeBytes >= 1_000_000_000L -> "${sizeBytes / 1_000_000_000L} GB"
            sizeBytes >= 1_000_000L -> "${sizeBytes / 1_000_000L} MB"
            sizeBytes >= 1_000L -> "${sizeBytes / 1_000L} KB"
            else -> "$sizeBytes B"
        }
    }

    private fun updateState(modelId: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            this[modelId] = state
        }
    }
}
