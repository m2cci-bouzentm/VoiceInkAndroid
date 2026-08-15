package com.voiceink.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceink.android.data.model.DownloadState
import com.voiceink.android.data.model.ModelDownloadManager
import com.voiceink.android.data.preferences.SettingsRepository
import com.voiceink.android.data.preferences.UsageRepository
import com.voiceink.android.data.preferences.UsageStats
import com.voiceink.android.data.subscription.SubscriptionRepository
import com.voiceink.android.data.subscription.SubscriptionStatus
import com.voiceink.android.data.subscription.SubscriptionTier
import com.voiceink.android.domain.model.LocalModel
import com.voiceink.android.domain.model.PredefinedModels
import com.voiceink.android.domain.output.TranscriptDestination
import com.voiceink.android.domain.output.TranscriptOutputRouter
import com.voiceink.android.services.TextInjectionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val selectedModelId: String = "gemini-2.5-flash",
    val selectedLanguage: String = "auto",
    val geminiApiKey: String = "",
    val openaiApiKey: String = "",
    val openRouterApiKey: String = "",
    val openRouterModelId: String = "",
    val downloadStates: Map<String, DownloadState> = emptyMap(),
    val downloadedModels: Set<String> = emptySet(),
    val isAccessibilityEnabled: Boolean = false,
    val isOverlayEnabled: Boolean = false,
    val isAutoPunctuationEnabled: Boolean = false,
    // Transcript destination
    val transcriptDestination: TranscriptDestination = TranscriptDestination.TEXT_INJECTION,
    val termuxScriptPath: String = TranscriptOutputRouter.DEFAULT_SCRIPT_PATH,
    val transcriptPostUrl: String = "",
    // Usage & subscription
    val usageStats: UsageStats = UsageStats(0f, 0f, 0L, 0),
    val subscriptionTier: SubscriptionTier = SubscriptionTier.FREE
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val modelDownloadManager: ModelDownloadManager,
    private val usageRepository: UsageRepository,
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Load saved settings
        viewModelScope.launch {
            combine(
                settingsRepository.selectedModelId,
                settingsRepository.selectedLanguage,
                settingsRepository.geminiApiKey,
                settingsRepository.openaiApiKey,
                modelDownloadManager.downloadStates,
                settingsRepository.overlayEnabled,
                settingsRepository.autoPunctuationEnabled,
                usageRepository.usageStats,
                subscriptionRepository.subscriptionStatus
            ) { values ->
                val modelId = values[0] as String
                val language = values[1] as String
                val geminiKey = values[2] as String
                val openaiKey = values[3] as String
                @Suppress("UNCHECKED_CAST")
                val downloadStates = values[4] as Map<String, DownloadState>
                val overlayEnabled = values[5] as Boolean
                val autoPunctuationEnabled = values[6] as Boolean
                val usageStats = values[7] as UsageStats
                val subscriptionStatus = values[8] as SubscriptionStatus
                val subscriptionTier = subscriptionStatus.tier

                SettingsUiState(
                    selectedModelId = modelId,
                    selectedLanguage = language,
                    geminiApiKey = geminiKey,
                    openaiApiKey = openaiKey,
                    downloadStates = downloadStates,
                    downloadedModels = getDownloadedModels(),
                    isAccessibilityEnabled = TextInjectionService.isServiceEnabled(),
                    isOverlayEnabled = overlayEnabled,
                    isAutoPunctuationEnabled = autoPunctuationEnabled,
                    usageStats = usageStats,
                    subscriptionTier = subscriptionTier
                )
            }.collect { fresh ->
                // Copy field by field rather than `_uiState.value = fresh`.
                // A whole-state replacement resets everything this combine does
                // not know about — the transcript destination, script path, POST
                // URL and OpenRouter settings are owned by the collectors below,
                // and would silently snap back to their defaults on any emission
                // here. That reverted the destination to "type into focused app"
                // whenever an unrelated flow ticked.
                _uiState.update { current ->
                    current.copy(
                        selectedModelId = fresh.selectedModelId,
                        selectedLanguage = fresh.selectedLanguage,
                        geminiApiKey = fresh.geminiApiKey,
                        openaiApiKey = fresh.openaiApiKey,
                        downloadStates = fresh.downloadStates,
                        downloadedModels = fresh.downloadedModels,
                        isAccessibilityEnabled = fresh.isAccessibilityEnabled,
                        isOverlayEnabled = fresh.isOverlayEnabled,
                        isAutoPunctuationEnabled = fresh.isAutoPunctuationEnabled,
                        usageStats = fresh.usageStats,
                        subscriptionTier = fresh.subscriptionTier
                    )
                }
            }
        }

        // Kept out of the combine above, which is already at nine sources and
        // indexes its values positionally.
        viewModelScope.launch {
            combine(
                settingsRepository.transcriptDestination,
                settingsRepository.termuxScriptPath,
                settingsRepository.transcriptPostUrl
            ) { destination, scriptPath, postUrl ->
                Triple(destination, scriptPath, postUrl)
            }.collect { (destination, scriptPath, postUrl) ->
                _uiState.update {
                    it.copy(
                        transcriptDestination = destination,
                        termuxScriptPath = scriptPath,
                        transcriptPostUrl = postUrl
                    )
                }
            }
        }

        viewModelScope.launch {
            combine(
                settingsRepository.openRouterApiKey,
                settingsRepository.openRouterModelId
            ) { key, modelId -> key to modelId }
                .collect { (key, modelId) ->
                    _uiState.update {
                        it.copy(openRouterApiKey = key, openRouterModelId = modelId)
                    }
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

    fun setAutoPunctuationEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isAutoPunctuationEnabled = enabled) }
        viewModelScope.launch {
            settingsRepository.setAutoPunctuationEnabled(enabled)
        }
    }

    fun setOpenRouterApiKey(key: String) {
        _uiState.update { it.copy(openRouterApiKey = key) }
        viewModelScope.launch {
            settingsRepository.setOpenRouterApiKey(key)
        }
    }

    fun setOpenRouterModelId(modelId: String) {
        _uiState.update { it.copy(openRouterModelId = modelId) }
        viewModelScope.launch {
            settingsRepository.setOpenRouterModelId(modelId)
        }
    }

    fun setTranscriptDestination(destination: TranscriptDestination) {
        _uiState.update { it.copy(transcriptDestination = destination) }
        viewModelScope.launch {
            settingsRepository.setTranscriptDestination(destination)
        }
    }

    fun setTermuxScriptPath(path: String) {
        _uiState.update { it.copy(termuxScriptPath = path) }
        viewModelScope.launch {
            settingsRepository.setTermuxScriptPath(path)
        }
    }

    fun setTranscriptPostUrl(url: String) {
        _uiState.update { it.copy(transcriptPostUrl = url) }
        viewModelScope.launch {
            settingsRepository.setTranscriptPostUrl(url)
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedModelId(modelId)
        }
    }

    fun setSelectedLanguage(languageCode: String) {
        _uiState.update { it.copy(selectedLanguage = languageCode) }
        viewModelScope.launch {
            settingsRepository.setSelectedLanguage(languageCode)
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
