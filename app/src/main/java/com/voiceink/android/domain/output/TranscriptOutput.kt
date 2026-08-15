package com.voiceink.android.domain.output

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.voiceink.android.data.preferences.SettingsRepository
import com.voiceink.android.services.TextInjectionService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where a finished transcript is sent.
 *
 * Mirrors Handy's `external_script` idea from the desktop voice-agent-loop: the
 * transcript does not have to be typed into whatever happens to be focused, it
 * can be handed to something else entirely. That is what lets an agent running
 * in a terminal receive dictation without the terminal ever being focused.
 */
enum class TranscriptDestination(val id: String, val label: String) {
    /** Type into the focused field via the accessibility service. Default. */
    TEXT_INJECTION("text_injection", "Type into focused app"),

    /** Run a script in Termux with the transcript as its first argument. */
    TERMUX_SCRIPT("termux_script", "Run Termux script"),

    /** POST the transcript to a URL. */
    HTTP_POST("http_post", "POST to URL");

    companion object {
        fun fromId(id: String?): TranscriptDestination =
            entries.firstOrNull { it.id == id } ?: TEXT_INJECTION
    }
}

data class DeliveryResult(val ok: Boolean, val detail: String)

@Singleton
class TranscriptOutputRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun deliver(text: String): DeliveryResult {
        if (text.isBlank()) return DeliveryResult(true, "empty transcript, nothing to deliver")

        return when (settingsRepository.transcriptDestination.first()) {
            TranscriptDestination.TEXT_INJECTION -> injectText(text)
            TranscriptDestination.TERMUX_SCRIPT -> runTermuxScript(text)
            TranscriptDestination.HTTP_POST -> httpPost(text)
        }
    }

    private fun injectText(text: String): DeliveryResult {
        if (TextInjectionService.isServiceEnabled() && TextInjectionService.injectText(text)) {
            return DeliveryResult(true, "injected")
        }
        copyToClipboard(text)
        return DeliveryResult(true, "injection unavailable, copied to clipboard")
    }

    /**
     * Hand the transcript to Termux via its RUN_COMMAND service.
     *
     * Only the F-Droid Termux exposes this service; the Play Store build does
     * not ship it at all and the start call fails with "Not found". The failure
     * is reported rather than swallowed, because a silently dropped transcript
     * is indistinguishable from the mic not working.
     */
    private suspend fun runTermuxScript(text: String): DeliveryResult {
        val path = settingsRepository.termuxScriptPath.first().trim()
        if (path.isBlank()) {
            return DeliveryResult(false, "no script path configured")
        }
        return try {
            val intent = Intent().apply {
                setClassName(TERMUX_PACKAGE, TERMUX_SERVICE)
                action = TERMUX_RUN_COMMAND
                putExtra("com.termux.RUN_COMMAND_PATH", path)
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf(text))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
            }
            // RunCommandService promotes itself to the foreground, so on O+ it
            // must be started as a foreground service or Android kills it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            DeliveryResult(true, "sent to Termux: $path")
        } catch (e: Exception) {
            Log.e(TAG, "Termux RUN_COMMAND failed", e)
            copyToClipboard(text)
            DeliveryResult(false, "Termux unavailable (${e.javaClass.simpleName}); copied to clipboard")
        }
    }

    /**
     * Fire the abort script: cut any speech and interrupt the running turn.
     *
     * Only meaningful for the Termux destination. The script lives beside the
     * delivery script rather than behind its own setting — one path to configure
     * instead of two, at the cost of expecting them in the same folder.
     *
     * Returns false when the destination is not Termux, so the caller can fall
     * back to whatever it did before.
     */
    suspend fun abort(): Boolean {
        if (settingsRepository.transcriptDestination.first() != TranscriptDestination.TERMUX_SCRIPT) {
            return false
        }
        val sendScript = settingsRepository.termuxScriptPath.first().trim()
        if (sendScript.isBlank()) return false

        val abortScript = sendScript.substringBeforeLast('/', "") + "/$ABORT_SCRIPT_NAME"
        return try {
            val intent = Intent().apply {
                setClassName(TERMUX_PACKAGE, TERMUX_SERVICE)
                action = TERMUX_RUN_COMMAND
                putExtra("com.termux.RUN_COMMAND_PATH", abortScript)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "abort sent: $abortScript")
            true
        } catch (e: Exception) {
            Log.e(TAG, "abort failed", e)
            false
        }
    }

    private suspend fun httpPost(text: String): DeliveryResult = withContext(Dispatchers.IO) {
        val url = settingsRepository.transcriptPostUrl.first().trim()
        if (url.isBlank()) return@withContext DeliveryResult(false, "no URL configured")

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 5000
                readTimeout = 10000
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(text) }
            val code = conn.responseCode
            if (code in 200..299) {
                DeliveryResult(true, "POSTed to $url")
            } else {
                copyToClipboard(text)
                DeliveryResult(false, "POST returned $code; copied to clipboard")
            }
        } catch (e: Exception) {
            Log.e(TAG, "POST failed", e)
            copyToClipboard(text)
            DeliveryResult(false, "POST failed (${e.javaClass.simpleName}); copied to clipboard")
        } finally {
            conn?.disconnect()
        }
    }

    private fun copyToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("VoiceInk", text))
    }

    companion object {
        private const val TAG = "TranscriptOutput"
        private const val TERMUX_PACKAGE = "com.termux"
        private const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
        private const val TERMUX_RUN_COMMAND = "com.termux.RUN_COMMAND"

        const val DEFAULT_SCRIPT_PATH =
            "/data/data/com.termux/files/home/voice-agent-loop/android/scripts/voice-send.sh"

        /** Expected alongside the delivery script. See [abort]. */
        private const val ABORT_SCRIPT_NAME = "voice-stop.sh"
    }
}
