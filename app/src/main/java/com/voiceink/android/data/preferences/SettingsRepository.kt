package com.voiceink.android.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voiceink.android.domain.output.TranscriptDestination
import com.voiceink.android.domain.output.TranscriptOutputRouter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Repository for app settings stored in DataStore
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val AUTO_PUNCTUATION_ENABLED = booleanPreferencesKey("auto_punctuation_enabled")
        val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
        val OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
        val OPENROUTER_MODEL_ID = stringPreferencesKey("openrouter_model_id")
        val TRANSCRIPT_DESTINATION = stringPreferencesKey("transcript_destination")
        val TERMUX_SCRIPT_PATH = stringPreferencesKey("termux_script_path")
        val TRANSCRIPT_POST_URL = stringPreferencesKey("transcript_post_url")
    }

    // API Keys
    val geminiApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.GEMINI_API_KEY] ?: ""
    }

    val openaiApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.OPENAI_API_KEY] ?: ""
    }

    suspend fun setGeminiApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GEMINI_API_KEY] = apiKey
        }
    }

    suspend fun setOpenaiApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.OPENAI_API_KEY] = apiKey
        }
    }

    // Selected Model
    val selectedModelId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.SELECTED_MODEL_ID] ?: "gemini-2.5-flash" // Default to Gemini
    }

    suspend fun setSelectedModelId(modelId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_MODEL_ID] = modelId
        }
    }

    // Overlay Enabled
    val overlayEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.OVERLAY_ENABLED] ?: false
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.OVERLAY_ENABLED] = enabled
        }
    }

    // Auto-punctuation Enabled
    val autoPunctuationEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_PUNCTUATION_ENABLED] ?: false
    }

    suspend fun setAutoPunctuationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_PUNCTUATION_ENABLED] = enabled
        }
    }

    // Selected Language for Whisper models
    val selectedLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.SELECTED_LANGUAGE] ?: "auto" // Default to auto-detect
    }

    suspend fun setSelectedLanguage(languageCode: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_LANGUAGE] = languageCode
        }
    }

    val openRouterApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.OPENROUTER_API_KEY] ?: ""
    }

    suspend fun setOpenRouterApiKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.OPENROUTER_API_KEY] = key
        }
    }

    // Typed by the user: OpenRouter's catalogue is too large and too fast-moving
    // to enumerate, e.g. "google/gemini-2.5-flash".
    val openRouterModelId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.OPENROUTER_MODEL_ID] ?: ""
    }

    suspend fun setOpenRouterModelId(modelId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.OPENROUTER_MODEL_ID] = modelId
        }
    }

    // Where finished transcripts are sent. See TranscriptDestination.
    val transcriptDestination: Flow<TranscriptDestination> = context.dataStore.data.map { prefs ->
        TranscriptDestination.fromId(prefs[Keys.TRANSCRIPT_DESTINATION])
    }

    suspend fun setTranscriptDestination(destination: TranscriptDestination) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TRANSCRIPT_DESTINATION] = destination.id
        }
    }

    val termuxScriptPath: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.TERMUX_SCRIPT_PATH] ?: TranscriptOutputRouter.DEFAULT_SCRIPT_PATH
    }

    suspend fun setTermuxScriptPath(path: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TERMUX_SCRIPT_PATH] = path
        }
    }

    val transcriptPostUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.TRANSCRIPT_POST_URL] ?: ""
    }

    suspend fun setTranscriptPostUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TRANSCRIPT_POST_URL] = url
        }
    }

    // Clear all settings
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
