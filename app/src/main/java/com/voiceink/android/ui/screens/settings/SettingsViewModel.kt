package com.voiceink.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceink.android.data.model.DownloadState
import com.voiceink.android.data.model.ModelDownloadManager
import com.voiceink.android.data.preferences.SettingsRepository
import com.voiceink.android.domain.model.LocalModel
import com.voiceink.android.domain.model.PredefinedModels
import com.voiceink.android.services.TextInjectionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val selectedModelId: String = "gemini-2.5-flash",
    val geminiApiKey: String = "",
    val openaiApiKey: String = "",
    val downloadStates: Map<String, DownloadState> = emptyMap(),
    val downloadedModels: Set<String> = emptySet(),
    val isAccessibilityEnabled: Boolean = false,
    val isOverlayEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val modelDownloadManager: ModelDownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Load saved settings
        viewModelScope.launch {
            combine(
                settingsRepository.selectedModelId,
                settingsRepository.geminiApiKey,
                settingsRepository.openaiApiKey,
                modelDownloadManager.downloadStates,
                settingsRepository.overlayEnabled
            ) { modelId, geminiKey, openaiKey, downloadStates, overlayEnabled ->
                SettingsUiState(
                    selectedModelId = modelId,
                    geminiApiKey = geminiKey,
                    openaiApiKey = openaiKey,
                    downloadStates = downloadStates,
                    downloadedModels = getDownloadedModels(),
                    isAccessibilityEnabled = TextInjectionService.isServiceEnabled(),
                    isOverlayEnabled = overlayEnabled
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun getDownloadedModels(): Set<String> {
        return PredefinedModels.localModels
            .filter { modelDownloadManager.isModelDownloaded(it) }
            .map { it.id }
            .toSet()
    }

    fun refreshAccessibilityStatus() {
        _uiState.update { it.copy(isAccessibilityEnabled = TextInjectionService.isServiceEnabled()) }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isOverlayEnabled = enabled) }
        viewModelScope.launch {
            settingsRepository.setOverlayEnabled(enabled)
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedModelId(modelId)
        }
    }

    fun setGeminiApiKey(apiKey: String) {
        _uiState.update { it.copy(geminiApiKey = apiKey) }
        viewModelScope.launch {
            settingsRepository.setGeminiApiKey(apiKey)
        }
    }

    fun setOpenaiApiKey(apiKey: String) {
        _uiState.update { it.copy(openaiApiKey = apiKey) }
        viewModelScope.launch {
            settingsRepository.setOpenaiApiKey(apiKey)
        }
    }

    fun downloadModel(model: LocalModel) {
        viewModelScope.launch {
            modelDownloadManager.downloadModel(model)
            // Refresh downloaded models list
            _uiState.update { it.copy(downloadedModels = getDownloadedModels()) }
        }
    }

    fun deleteModel(model: LocalModel) {
        viewModelScope.launch {
            modelDownloadManager.deleteModel(model)
            // Refresh downloaded models list
            _uiState.update { it.copy(downloadedModels = getDownloadedModels()) }
        }
    }

    fun getModelSize(model: LocalModel): Long {
        return modelDownloadManager.getModelSize(model)
    }
}
