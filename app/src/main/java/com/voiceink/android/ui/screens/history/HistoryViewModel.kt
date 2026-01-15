package com.voiceink.android.ui.screens.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceink.android.data.database.TranscriptionEntity
import com.voiceink.android.data.history.TranscriptionHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val transcriptions: List<TranscriptionEntity> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isLoading: Boolean = true,
    val showDeleteConfirmation: Boolean = false,
    val itemToDelete: TranscriptionEntity? = null,
    val showDeleteAllConfirmation: Boolean = false,
    val copiedId: String? = null // For showing "Copied!" feedback
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: TranscriptionHistoryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    init {
        loadTranscriptions()
    }

    private fun loadTranscriptions() {
        viewModelScope.launch {
            historyRepository.allTranscriptions.collect { transcriptions ->
                _uiState.update {
                    it.copy(
                        transcriptions = transcriptions,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isNotBlank()) {
            searchTranscriptions(query)
        } else {
            loadTranscriptions()
        }
    }

    private fun searchTranscriptions(query: String) {
        viewModelScope.launch {
            historyRepository.search(query).collect { results ->
                _uiState.update { it.copy(transcriptions = results) }
            }
        }
    }

    fun toggleSearch() {
        _uiState.update {
            if (it.isSearchActive) {
                // Closing search, clear query and reload all
                loadTranscriptions()
                it.copy(isSearchActive = false, searchQuery = "")
            } else {
                it.copy(isSearchActive = true)
            }
        }
    }

    fun copyToClipboard(entry: TranscriptionEntity) {
        val clip = ClipData.newPlainText("Transcription", entry.text)
        clipboardManager.setPrimaryClip(clip)

        // Show feedback
        _uiState.update { it.copy(copiedId = entry.id) }

        // Clear feedback after delay
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            _uiState.update { it.copy(copiedId = null) }
        }
    }

    fun showDeleteConfirmation(entry: TranscriptionEntity) {
        _uiState.update {
            it.copy(showDeleteConfirmation = true, itemToDelete = entry)
        }
    }

    fun hideDeleteConfirmation() {
        _uiState.update {
            it.copy(showDeleteConfirmation = false, itemToDelete = null)
        }
    }

    fun confirmDelete() {
        val item = _uiState.value.itemToDelete ?: return
        viewModelScope.launch {
            historyRepository.delete(item)
            _uiState.update {
                it.copy(showDeleteConfirmation = false, itemToDelete = null)
            }
        }
    }

    fun showDeleteAllConfirmation() {
        _uiState.update { it.copy(showDeleteAllConfirmation = true) }
    }

    fun hideDeleteAllConfirmation() {
        _uiState.update { it.copy(showDeleteAllConfirmation = false) }
    }

    fun confirmDeleteAll() {
        viewModelScope.launch {
            historyRepository.deleteAll()
            _uiState.update { it.copy(showDeleteAllConfirmation = false) }
        }
    }
}
