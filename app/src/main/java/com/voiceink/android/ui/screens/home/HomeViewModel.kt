package com.voiceink.android.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceink.android.data.audio.AudioRecorder
import com.voiceink.android.data.audio.RecordingState
import com.voiceink.android.data.preferences.SettingsRepository
import com.voiceink.android.domain.model.PredefinedModels
import com.voiceink.android.domain.model.TranscriptionModel
import com.voiceink.android.domain.transcription.TranscriptionRegistry
import com.voiceink.android.domain.transcription.TranscriptionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val transcription: String = "",
    val error: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val transcriptionRegistry: TranscriptionRegistry,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val recordingState: StateFlow<RecordingState> = audioRecorder.state

    val selectedModel: StateFlow<TranscriptionModel?> = settingsRepository.selectedModelId
        .map { modelId ->
            PredefinedModels.allModels.find { it.id == modelId }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PredefinedModels.gemini25Flash
        )

    fun hasPermission(): Boolean = audioRecorder.hasPermission()

    fun toggleRecording() {
        viewModelScope.launch {
            when (recordingState.value) {
                RecordingState.IDLE -> startRecording()
                RecordingState.RECORDING -> stopRecordingAndTranscribe()
                RecordingState.PROCESSING -> { /* Do nothing while processing */ }
            }
        }
    }

    private suspend fun startRecording() {
        _uiState.update { it.copy(error = null, transcription = "") }

        val file = audioRecorder.startRecording()
        if (file == null) {
            _uiState.update { it.copy(error = "Failed to start recording") }
        }
    }

    private suspend fun stopRecordingAndTranscribe() {
        val audioFile = audioRecorder.stopRecording()

        if (audioFile == null || !audioFile.exists()) {
            _uiState.update { it.copy(error = "No audio recorded") }
            return
        }

        val model = selectedModel.value
        if (model == null) {
            _uiState.update { it.copy(error = "No model selected") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        when (val result = transcriptionRegistry.transcribe(audioFile, model)) {
            is TranscriptionResult.Success -> {
                _uiState.update {
                    it.copy(
                        transcription = result.text,
                        isLoading = false,
                        error = null
                    )
                }
            }
            is TranscriptionResult.Error -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }

        // Cleanup the audio file
        audioFile.delete()
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.cleanupOldRecordings()
    }
}
