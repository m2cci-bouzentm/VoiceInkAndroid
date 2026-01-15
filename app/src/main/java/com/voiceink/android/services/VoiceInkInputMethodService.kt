package com.voiceink.android.services

import android.Manifest
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.voiceink.android.R
import com.voiceink.android.data.history.TranscriptionHistoryRepository
import com.voiceink.android.data.preferences.SettingsRepository
import com.voiceink.android.domain.model.PredefinedModels
import com.voiceink.android.domain.model.TranscriptionModel
import com.voiceink.android.domain.transcription.TranscriptionRegistry
import com.voiceink.android.domain.transcription.TranscriptionResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * VoiceInk Input Method Service
 *
 * A minimal "keyboard" that only provides voice input.
 * User switches to this IME when they want to speak, then switches back to their regular keyboard.
 *
 * Setup: Settings → System → Languages & Input → On-screen keyboard → Manage keyboards → Enable VoiceInk
 */
class VoiceInkInputMethodService : InputMethodService() {

    companion object {
        private const val TAG = "VoiceInkIME"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    // Hilt entry point for dependency injection
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface VoiceInkIMEEntryPoint {
        fun transcriptionRegistry(): TranscriptionRegistry
        fun settingsRepository(): SettingsRepository
        fun historyRepository(): TranscriptionHistoryRepository
    }

    private lateinit var transcriptionRegistry: TranscriptionRegistry
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var historyRepository: TranscriptionHistoryRepository

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Recording state
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var isProcessing = false
    private var recordingJob: Job? = null
    private val recordedData = mutableListOf<Short>()

    // UI elements
    private var statusText: TextView? = null
    private var micButton: ImageButton? = null
    private var switchButton: TextView? = null
    private var hintText: TextView? = null
    private var cancelButton: TextView? = null

    // Double-tap detection
    private var lastTapTime = 0L
    private val doubleTapTimeout = 300L

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VoiceInk IME onCreate")

        // Get dependencies via Hilt EntryPoint
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            VoiceInkIMEEntryPoint::class.java
        )
        transcriptionRegistry = entryPoint.transcriptionRegistry()
        settingsRepository = entryPoint.settingsRepository()
        historyRepository = entryPoint.historyRepository()

        Log.d(TAG, "VoiceInk IME created successfully")
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "onStartInputView called, restarting=$restarting")
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        Log.d(TAG, "onFinishInputView called")
        stopRecording()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopRecording()
        Log.d(TAG, "VoiceInk IME destroyed")
    }

    override fun onCreateInputView(): View {
        Log.d(TAG, "onCreateInputView called")

        val view = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)

        statusText = view.findViewById(R.id.statusText)
        micButton = view.findViewById(R.id.micButton)
        switchButton = view.findViewById(R.id.switchButton)
        hintText = view.findViewById(R.id.hintText)
        cancelButton = view.findViewById(R.id.cancelButton)

        micButton?.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < doubleTapTimeout) {
                // Double-tap detected - clear input
                Log.d(TAG, "Double-tap detected, clearing input")
                clearInput()
                lastTapTime = 0L
            } else {
                // Single tap - toggle recording
                lastTapTime = currentTime
                Log.d(TAG, "Mic button clicked")
                toggleRecording()
            }
        }

        // Long-press on mic button to abort recording
        micButton?.setOnLongClickListener {
            if (isRecording) {
                Log.d(TAG, "Long-press detected, aborting recording")
                abortRecording()
                true
            } else {
                false
            }
        }

        // Cancel button click
        cancelButton?.setOnClickListener {
            Log.d(TAG, "Cancel button clicked")
            abortRecording()
        }

        switchButton?.setOnClickListener {
            Log.d(TAG, "Switch button clicked")
            cancelAndClose()
        }

        Log.d(TAG, "Input view created successfully")
        return view
    }

    private fun clearInput() {
        // Select all text and delete it
        currentInputConnection?.let { ic ->
            // Get the text before and after cursor
            val before = ic.getTextBeforeCursor(10000, 0)
            val after = ic.getTextAfterCursor(10000, 0)
            val totalLength = (before?.length ?: 0) + (after?.length ?: 0)

            if (totalLength > 0) {
                // Delete surrounding text
                ic.deleteSurroundingText(before?.length ?: 0, after?.length ?: 0)
                Log.d(TAG, "Cleared $totalLength characters")
                Toast.makeText(this, "Input cleared", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleRecording() {
        if (isProcessing) return

        if (isRecording) {
            stopRecordingAndTranscribe()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show()
            return
        }

        recordedData.clear()
        audioRecord?.startRecording()
        isRecording = true
        updateUI()

        recordingJob = serviceScope.launch(Dispatchers.IO) {
            val buffer = ShortArray(bufferSize / 2)
            while (isRecording && isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    synchronized(recordedData) {
                        for (i in 0 until read) {
                            recordedData.add(buffer[i])
                        }
                    }
                }
            }
        }

        Log.d(TAG, "Recording started")
    }

    private fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun stopRecordingAndTranscribe() {
        stopRecording()
        isProcessing = true
        updateUI()

        serviceScope.launch {
            try {
                val audioFile = saveToWavFile()
                if (audioFile == null) {
                    statusText?.text = "Recording failed"
                    isProcessing = false
                    updateUI()
                    return@launch
                }

                // Get selected model
                val modelId = settingsRepository.selectedModelId.first()
                val model = PredefinedModels.allModels.find { it.id == modelId }
                    ?: PredefinedModels.gemini25Flash

                Log.d(TAG, "Transcribing with model: ${model.name}")

                val result = transcriptionRegistry.transcribe(audioFile, model)

                when (result) {
                    is TranscriptionResult.Success -> {
                        Log.d(TAG, "Transcription success: ${result.text}")

                        // Save to history before deleting audio file
                        saveToHistory(result.text, model, audioFile)

                        // Insert text into the focused text field
                        currentInputConnection?.commitText(result.text, 1)
                        statusText?.text = "Tap to speak"
                    }
                    is TranscriptionResult.Error -> {
                        Log.e(TAG, "Transcription error: ${result.message}")
                        statusText?.text = "Error: ${result.message}"
                        // Reset after delay
                        delay(2000)
                        statusText?.text = "Tap to speak"
                    }
                }

                audioFile.delete()

            } catch (e: Exception) {
                Log.e(TAG, "Error during transcription", e)
                statusText?.text = "Error occurred"
                delay(2000)
                statusText?.text = "Tap to speak"
            }

            isProcessing = false
            updateUI()
        }
    }

    private fun updateUI() {
        serviceScope.launch(Dispatchers.Main) {
            when {
                isProcessing -> {
                    statusText?.text = "Processing..."
                    micButton?.setBackgroundResource(R.drawable.overlay_button_background)
                    cancelButton?.visibility = View.GONE
                    hintText?.text = "Please wait..."
                }
                isRecording -> {
                    statusText?.text = "Listening... Tap to stop"
                    micButton?.setBackgroundResource(R.drawable.overlay_button_recording)
                    cancelButton?.visibility = View.VISIBLE
                    hintText?.text = "Long-press to cancel"
                }
                else -> {
                    statusText?.text = "Tap to speak"
                    micButton?.setBackgroundResource(R.drawable.overlay_button_background)
                    cancelButton?.visibility = View.GONE
                    hintText?.text = "Double-tap to clear"
                }
            }
        }
    }

    private fun abortRecording() {
        if (!isRecording) return

        stopRecording()
        recordedData.clear()
        isProcessing = false
        updateUI()

        Toast.makeText(this, "Recording cancelled", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Recording aborted")
    }

    private suspend fun saveToHistory(text: String, model: TranscriptionModel, audioFile: File) {
        try {
            historyRepository.save(
                text = text,
                model = model,
                audioFile = audioFile,
                wasStreaming = false,
                hadAutoPunctuation = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to history", e)
        }
    }

    private fun cancelAndClose() {
        stopRecording()
        isProcessing = false
        updateUI()
        // Switch to previous input method
        switchToPreviousInputMethod()
    }

    private fun saveToWavFile(): File? {
        val data: ShortArray
        synchronized(recordedData) {
            if (recordedData.isEmpty()) return null
            data = recordedData.toShortArray()
        }

        val file = File(cacheDir, "ime_recording_${System.currentTimeMillis()}.wav")

        try {
            FileOutputStream(file).use { fos ->
                val byteData = ByteArray(data.size * 2)
                ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(data)

                // WAV header
                val totalDataLen = byteData.size + 36
                val header = ByteArray(44)
                val byteRate = SAMPLE_RATE * 1 * 16 / 8

                // RIFF header
                header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
                header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
                header[4] = (totalDataLen and 0xff).toByte()
                header[5] = ((totalDataLen shr 8) and 0xff).toByte()
                header[6] = ((totalDataLen shr 16) and 0xff).toByte()
                header[7] = ((totalDataLen shr 24) and 0xff).toByte()
                header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
                header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()

                // fmt chunk
                header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
                header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
                header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
                header[20] = 1; header[21] = 0 // PCM
                header[22] = 1; header[23] = 0 // mono
                header[24] = (SAMPLE_RATE and 0xff).toByte()
                header[25] = ((SAMPLE_RATE shr 8) and 0xff).toByte()
                header[26] = ((SAMPLE_RATE shr 16) and 0xff).toByte()
                header[27] = ((SAMPLE_RATE shr 24) and 0xff).toByte()
                header[28] = (byteRate and 0xff).toByte()
                header[29] = ((byteRate shr 8) and 0xff).toByte()
                header[30] = ((byteRate shr 16) and 0xff).toByte()
                header[31] = ((byteRate shr 24) and 0xff).toByte()
                header[32] = 2; header[33] = 0 // block align
                header[34] = 16; header[35] = 0 // bits per sample

                // data chunk
                header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
                header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
                header[40] = (byteData.size and 0xff).toByte()
                header[41] = ((byteData.size shr 8) and 0xff).toByte()
                header[42] = ((byteData.size shr 16) and 0xff).toByte()
                header[43] = ((byteData.size shr 24) and 0xff).toByte()

                fos.write(header)
                fos.write(byteData)
            }
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save WAV file", e)
            return null
        }
    }
}
