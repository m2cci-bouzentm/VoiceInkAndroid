package com.voiceink.android.data.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recording state
 */
enum class RecordingState {
    IDLE,
    RECORDING,
    PROCESSING
}

/**
 * Handles audio recording using Android AudioRecord API
 * Outputs 16kHz mono 16-bit PCM WAV files for optimal transcription
 */
@Singleton
class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    // Audio amplitude for waveform visualization (0.0 to 1.0)
    private val _amplitudeFlow = MutableStateFlow(0f)
    val amplitudeFlow: StateFlow<Float> = _amplitudeFlow.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false
    private var outputFile: File? = null

    /**
     * Check if microphone permission is granted
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start recording audio
     * @return The file where audio will be saved, or null if failed
     */
    suspend fun startRecording(): File? = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            return@withContext null
        }

        if (_state.value == RecordingState.RECORDING) {
            return@withContext outputFile
        }

        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return@withContext null
            }

            // Create output file
            outputFile = File(context.cacheDir, "recording_${System.currentTimeMillis()}.wav")

            isRecording = true
            _state.value = RecordingState.RECORDING

            audioRecord?.startRecording()

            // Start recording thread
            recordingThread = Thread {
                writeAudioToFile(outputFile!!, bufferSize)
            }.apply { start() }

            outputFile
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            stopRecording()
            null
        }
    }

    /**
     * Stop recording and return the recorded file
     */
    suspend fun stopRecording(): File? = withContext(Dispatchers.IO) {
        if (_state.value != RecordingState.RECORDING) {
            return@withContext null
        }

        _state.value = RecordingState.PROCESSING

        isRecording = false
        recordingThread?.join(1000)
        recordingThread = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        _state.value = RecordingState.IDLE

        outputFile
    }

    /**
     * Cancel recording without processing - discards the audio
     * @return true if recording was cancelled, false if not recording
     */
    suspend fun cancelRecording(): Boolean = withContext(Dispatchers.IO) {
        if (_state.value != RecordingState.RECORDING) {
            return@withContext false
        }

        isRecording = false
        recordingThread?.join(1000)
        recordingThread = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        // Delete the partial recording file
        outputFile?.delete()
        outputFile = null

        _state.value = RecordingState.IDLE
        true
    }

    private fun writeAudioToFile(file: File, bufferSize: Int) {
        val buffer = ShortArray(bufferSize / 2)
        val audioData = mutableListOf<Short>()
        var sampleCount = 0
        val amplitudeUpdateInterval = SAMPLE_RATE / 10 // Update ~10 times per second

        while (isRecording) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (read > 0) {
                for (i in 0 until read) {
                    audioData.add(buffer[i])
                }

                // Calculate and emit amplitude periodically
                sampleCount += read
                if (sampleCount >= amplitudeUpdateInterval) {
                    val amplitude = calculateRMS(buffer, read)
                    _amplitudeFlow.value = amplitude
                    sampleCount = 0
                }
            }
        }

        // Reset amplitude when recording stops
        _amplitudeFlow.value = 0f

        // Write WAV file
        writeWavFile(file, audioData.toShortArray())
    }

    /**
     * Calculate RMS (Root Mean Square) amplitude from audio samples
     * @return Normalized amplitude between 0.0 and 1.0
     */
    private fun calculateRMS(buffer: ShortArray, size: Int): Float {
        if (size == 0) return 0f

        var sum = 0.0
        for (i in 0 until size) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }

        val rms = sqrt(sum / size)
        // Normalize to 0-1 range (short max value is 32768)
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    private fun writeWavFile(file: File, audioData: ShortArray) {
        val byteData = ByteArray(audioData.size * 2)
        for (i in audioData.indices) {
            byteData[i * 2] = (audioData[i].toInt() and 0xFF).toByte()
            byteData[i * 2 + 1] = (audioData[i].toInt() shr 8 and 0xFF).toByte()
        }

        FileOutputStream(file).use { fos ->
            // Write WAV header
            val totalDataLen = byteData.size + 36
            val channels = 1
            val byteRate = SAMPLE_RATE * channels * 2

            val header = ByteArray(44)

            // RIFF header
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = (totalDataLen shr 8 and 0xff).toByte()
            header[6] = (totalDataLen shr 16 and 0xff).toByte()
            header[7] = (totalDataLen shr 24 and 0xff).toByte()
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()

            // fmt subchunk
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            header[16] = 16 // Subchunk1Size (16 for PCM)
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1 // AudioFormat (1 for PCM)
            header[21] = 0
            header[22] = channels.toByte() // NumChannels
            header[23] = 0
            header[24] = (SAMPLE_RATE and 0xff).toByte()
            header[25] = (SAMPLE_RATE shr 8 and 0xff).toByte()
            header[26] = (SAMPLE_RATE shr 16 and 0xff).toByte()
            header[27] = (SAMPLE_RATE shr 24 and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = (byteRate shr 8 and 0xff).toByte()
            header[30] = (byteRate shr 16 and 0xff).toByte()
            header[31] = (byteRate shr 24 and 0xff).toByte()
            header[32] = (channels * 2).toByte() // BlockAlign
            header[33] = 0
            header[34] = 16 // BitsPerSample
            header[35] = 0

            // data subchunk
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            header[40] = (byteData.size and 0xff).toByte()
            header[41] = (byteData.size shr 8 and 0xff).toByte()
            header[42] = (byteData.size shr 16 and 0xff).toByte()
            header[43] = (byteData.size shr 24 and 0xff).toByte()

            fos.write(header)
            fos.write(byteData)
        }
    }

    /**
     * Clean up old recording files
     */
    fun cleanupOldRecordings() {
        context.cacheDir.listFiles()?.filter {
            it.name.startsWith("recording_") && it.name.endsWith(".wav")
        }?.forEach { it.delete() }
    }
}
