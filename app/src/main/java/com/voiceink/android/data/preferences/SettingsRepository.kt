package com.voiceink.android.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    // Clear all settings
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
