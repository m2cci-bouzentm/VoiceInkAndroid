package com.voiceink.android.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

private val Context.usageDataStore: DataStore<Preferences> by preferencesDataStore(name = "usage")

/**
 * Usage statistics for tracking transcription usage
 */
data class UsageStats(
    val localMinutesUsed: Float,
    val cloudMinutesUsed: Float,
    val lastResetTimestamp: Long,
    val totalTranscriptions: Int
)

/**
 * Repository for tracking transcription usage limits
 */
@Singleton
class UsageRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Free tier limits (per month)
        const val FREE_LOCAL_MINUTES = 60f
        const val FREE_CLOUD_MINUTES = 5f

        // Sample rate for WAV files (must match AudioRecorder)
        private const val SAMPLE_RATE = 16000
        private const val BYTES_PER_SAMPLE = 2 // 16-bit audio
        private const val CHANNELS = 1 // Mono
    }

    private object Keys {
        val LOCAL_MINUTES_USED = floatPreferencesKey("local_minutes_used")
        val CLOUD_MINUTES_USED = floatPreferencesKey("cloud_minutes_used")
        val LAST_USAGE_RESET = longPreferencesKey("last_usage_reset")
        val TOTAL_TRANSCRIPTIONS = intPreferencesKey("total_transcriptions")
    }

    val localMinutesUsed: Flow<Float> = context.usageDataStore.data.map { prefs ->
        prefs[Keys.LOCAL_MINUTES_USED] ?: 0f
    }

    val cloudMinutesUsed: Flow<Float> = context.usageDataStore.data.map { prefs ->
        prefs[Keys.CLOUD_MINUTES_USED] ?: 0f
    }

    val lastResetTimestamp: Flow<Long> = context.usageDataStore.data.map { prefs ->
        prefs[Keys.LAST_USAGE_RESET] ?: System.currentTimeMillis()
    }

    val totalTranscriptions: Flow<Int> = context.usageDataStore.data.map { prefs ->
        prefs[Keys.TOTAL_TRANSCRIPTIONS] ?: 0
    }

    /**
     * Get complete usage stats as a flow
     */
    val usageStats: Flow<UsageStats> = context.usageDataStore.data.map { prefs ->
        UsageStats(
            localMinutesUsed = prefs[Keys.LOCAL_MINUTES_USED] ?: 0f,
            cloudMinutesUsed = prefs[Keys.CLOUD_MINUTES_USED] ?: 0f,
            lastResetTimestamp = prefs[Keys.LAST_USAGE_RESET] ?: System.currentTimeMillis(),
            totalTranscriptions = prefs[Keys.TOTAL_TRANSCRIPTIONS] ?: 0
        )
    }

    /**
     * Check and reset usage if a new month has started
     */
    suspend fun checkAndResetIfNewMonth() {
        val lastReset = lastResetTimestamp.first()

        val lastResetCal = Calendar.getInstance().apply { timeInMillis = lastReset }
        val nowCal = Calendar.getInstance()

        val isNewMonth = nowCal.get(Calendar.YEAR) > lastResetCal.get(Calendar.YEAR) ||
                (nowCal.get(Calendar.YEAR) == lastResetCal.get(Calendar.YEAR) &&
                        nowCal.get(Calendar.MONTH) > lastResetCal.get(Calendar.MONTH))

        if (isNewMonth) {
            resetMonthlyUsage()
        }
    }

    /**
     * Reset monthly usage counters
     */
    private suspend fun resetMonthlyUsage() {
        context.usageDataStore.edit { prefs ->
            prefs[Keys.LOCAL_MINUTES_USED] = 0f
            prefs[Keys.CLOUD_MINUTES_USED] = 0f
            prefs[Keys.LAST_USAGE_RESET] = System.currentTimeMillis()
        }
    }

    /**
     * Track usage after a successful transcription
     * @param isLocal true for local models, false for cloud models
     * @param durationMinutes duration of the audio in minutes
     */
    suspend fun trackUsage(isLocal: Boolean, durationMinutes: Float) {
        context.usageDataStore.edit { prefs ->
            if (isLocal) {
                val current = prefs[Keys.LOCAL_MINUTES_USED] ?: 0f
                prefs[Keys.LOCAL_MINUTES_USED] = current + durationMinutes
            } else {
                val current = prefs[Keys.CLOUD_MINUTES_USED] ?: 0f
                prefs[Keys.CLOUD_MINUTES_USED] = current + durationMinutes
            }

            val total = prefs[Keys.TOTAL_TRANSCRIPTIONS] ?: 0
            prefs[Keys.TOTAL_TRANSCRIPTIONS] = total + 1
        }
    }

    /**
     * Check if user can transcribe with the given provider
     * @param isLocal true for local models, false for cloud models
     * @param isPro true if user has pro subscription
     * @return Pair of (canTranscribe, remainingMinutes)
     */
    suspend fun canTranscribe(isLocal: Boolean, isPro: Boolean): Pair<Boolean, Float> {
        if (isPro) {
            return Pair(true, Float.MAX_VALUE)
        }

        checkAndResetIfNewMonth()

        val currentUsage = if (isLocal) {
            localMinutesUsed.first()
        } else {
            cloudMinutesUsed.first()
        }

        val limit = if (isLocal) FREE_LOCAL_MINUTES else FREE_CLOUD_MINUTES
        val remaining = limit - currentUsage

        return Pair(remaining > 0, remaining.coerceAtLeast(0f))
    }

    /**
     * Check if a specific audio file can be transcribed within limits
     * @param audioFile the WAV file to check
     * @param isLocal true for local models, false for cloud models
     * @param isPro true if user has pro subscription
     * @return Pair of (canTranscribe, errorMessage or null)
     */
    suspend fun canTranscribeFile(
        audioFile: File,
        isLocal: Boolean,
        isPro: Boolean
    ): Pair<Boolean, String?> {
        if (isPro) {
            return Pair(true, null)
        }

        checkAndResetIfNewMonth()

        val durationMinutes = getAudioDurationMinutes(audioFile)
        val currentUsage = if (isLocal) {
            localMinutesUsed.first()
        } else {
            cloudMinutesUsed.first()
        }

        val limit = if (isLocal) FREE_LOCAL_MINUTES else FREE_CLOUD_MINUTES
        val remaining = limit - currentUsage

        return if (durationMinutes <= remaining) {
            Pair(true, null)
        } else {
            val providerName = if (isLocal) "local" else "cloud"
            val usedFormatted = String.format("%.1f", currentUsage)
            val limitFormatted = String.format("%.0f", limit)
            Pair(
                false,
                "Free $providerName limit reached ($usedFormatted/$limitFormatted min). Upgrade to Pro for unlimited transcription!"
            )
        }
    }

    /**
     * Get audio duration in minutes from a WAV file
     */
    fun getAudioDurationMinutes(audioFile: File): Float {
        if (!audioFile.exists() || audioFile.length() < 44) {
            return 0f
        }

        // WAV header is 44 bytes, rest is audio data
        val audioDataSize = audioFile.length() - 44
        val bytesPerSecond = SAMPLE_RATE * BYTES_PER_SAMPLE * CHANNELS
        val durationSeconds = audioDataSize.toFloat() / bytesPerSecond

        return durationSeconds / 60f
    }

    /**
     * Get the next reset date (first day of next month)
     */
    fun getNextResetDate(): Calendar {
        return Calendar.getInstance().apply {
            add(Calendar.MONTH, 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    /**
     * Clear all usage data (for testing)
     */
    suspend fun clearAll() {
        context.usageDataStore.edit { it.clear() }
    }
}
