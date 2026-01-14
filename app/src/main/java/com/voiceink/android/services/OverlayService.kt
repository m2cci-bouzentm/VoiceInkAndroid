package com.voiceink.android.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.voiceink.android.MainActivity
import com.voiceink.android.R
import com.voiceink.android.data.audio.AudioRecorder
import com.voiceink.android.data.audio.RecordingState
import com.voiceink.android.data.preferences.SettingsRepository
import com.voiceink.android.domain.model.PredefinedModels
import com.voiceink.android.domain.transcription.TranscriptionRegistry
import com.voiceink.android.domain.transcription.TranscriptionResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import android.content.ClipData
import android.content.ClipboardManager

/**
 * Foreground service that displays a floating overlay button for recording.
 * The button is draggable and can be used to start/stop recording from any app.
 */
@AndroidEntryPoint
class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "voiceink_overlay_channel"

        const val ACTION_START_SERVICE = "com.voiceink.android.START_OVERLAY"
        const val ACTION_STOP_SERVICE = "com.voiceink.android.STOP_OVERLAY"

        private var instance: OverlayService? = null

        fun isRunning(): Boolean = instance != null

        /**
         * Start the overlay service
         */
        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the overlay service
         */
        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }

    @Inject
    lateinit var audioRecorder: AudioRecorder

    @Inject
    lateinit var transcriptionRegistry: TranscriptionRegistry

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentRecordingFile: File? = null

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // Views
    private var iconMic: ImageView? = null
    private var iconStop: ImageView? = null
    private var progressIndicator: ProgressBar? = null
    private var overlayBackground: View? = null

    // Drag handling
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private val dragThreshold = 10 // pixels

    // Double-tap detection
    private var lastTapTime = 0L
    private val doubleTapTimeout = 300L // milliseconds

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        Log.d(TAG, "OverlayService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_SERVICE -> {
                startForegroundWithNotification()
                createOverlayButton()
                observeRecordingState()
            }
            ACTION_STOP_SERVICE -> {
                removeOverlay()
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when VoiceInk overlay is active"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createOverlayButton() {
        if (overlayView != null) return // Already created

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_button, null)

        // Get references to views
        iconMic = overlayView?.findViewById(R.id.icon_mic)
        iconStop = overlayView?.findViewById(R.id.icon_stop)
        progressIndicator = overlayView?.findViewById(R.id.progress_indicator)
        overlayBackground = overlayView?.findViewById(R.id.overlay_background)

        // Create layout params for the overlay
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        // Set up touch listener for drag and click
        overlayView?.setOnTouchListener { _, event ->
            handleTouch(event)
        }

        // Add the view to the window
        try {
            windowManager.addView(overlayView, layoutParams)
            Log.d(TAG, "Overlay button added to window")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams?.x ?: 0
                initialY = layoutParams?.y ?: 0
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = (event.rawX - initialTouchX).toInt()
                val deltaY = (event.rawY - initialTouchY).toInt()

                if (!isDragging && (kotlin.math.abs(deltaX) > dragThreshold || kotlin.math.abs(deltaY) > dragThreshold)) {
                    isDragging = true
                }

                if (isDragging) {
                    layoutParams?.x = initialX + deltaX
                    layoutParams?.y = initialY + deltaY
                    try {
                        windowManager.updateViewLayout(overlayView, layoutParams)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update view layout", e)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastTapTime < doubleTapTimeout) {
                        // Double-tap detected - clear input
                        Log.d(TAG, "Double-tap detected, clearing input")
                        clearFocusedInput()
                        lastTapTime = 0L // Reset to prevent triple-tap
                    } else {
                        // Single tap - toggle recording
                        lastTapTime = currentTime
                        toggleRecording()
                    }
                }
                return true
            }
        }
        return false
    }

    private fun toggleRecording() {
        serviceScope.launch {
            when (audioRecorder.state.value) {
                RecordingState.IDLE -> startRecording()
                RecordingState.RECORDING -> stopRecordingAndTranscribe()
                RecordingState.PROCESSING -> { /* Do nothing while processing */ }
            }
        }
    }

    private suspend fun startRecording() {
        currentRecordingFile = audioRecorder.startRecording()
        if (currentRecordingFile != null) {
            Log.d(TAG, "Recording started: ${currentRecordingFile?.absolutePath}")
        } else {
            Log.e(TAG, "Failed to start recording")
        }
    }

    private suspend fun stopRecordingAndTranscribe() {
        val audioFile = audioRecorder.stopRecording()
        Log.d(TAG, "Recording stopped: ${audioFile?.absolutePath}")

        if (audioFile != null && audioFile.exists()) {
            // Show processing state on overlay while transcribing
            updateOverlayUI(RecordingState.PROCESSING)
            try {
                transcribeAudio(audioFile)
            } finally {
                // Always reset to idle after transcription (success or failure)
                updateOverlayUI(RecordingState.IDLE)
            }
        }
    }

    private suspend fun transcribeAudio(audioFile: File) {
        try {
            // Get selected model
            val modelId = settingsRepository.selectedModelId.first()
            val model = PredefinedModels.allModels.find { it.id == modelId }
                ?: PredefinedModels.gemini25Flash

            Log.d(TAG, "Transcribing with model: ${model.name}")

            val result = transcriptionRegistry.transcribe(audioFile, model)

            when (result) {
                is TranscriptionResult.Success -> {
                    Log.d(TAG, "Transcription successful: ${result.text}")

                    // Try to inject text into focused field
                    if (TextInjectionService.isServiceEnabled()) {
                        val injected = TextInjectionService.injectText(result.text)
                        if (injected) {
                            Log.d(TAG, "Text injected successfully")
                        } else {
                            Log.d(TAG, "Text injection failed, copying to clipboard")
                            copyToClipboard(result.text)
                        }
                    } else {
                        // Accessibility not enabled, copy to clipboard
                        copyToClipboard(result.text)
                    }
                }
                is TranscriptionResult.Error -> {
                    Log.e(TAG, "Transcription failed: ${result.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transcription error", e)
        }

        // Cleanup the audio file
        audioFile.delete()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("VoiceInk Transcription", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun clearFocusedInput() {
        if (TextInjectionService.isServiceEnabled()) {
            val cleared = TextInjectionService.clearFocusedInput()
            Log.d(TAG, "Clear input result: $cleared")
        } else {
            Log.d(TAG, "Accessibility service not enabled, cannot clear input")
        }
    }

    private fun observeRecordingState() {
        serviceScope.launch {
            audioRecorder.state.collect { state ->
                updateOverlayUI(state)
            }
        }
    }

    private fun updateOverlayUI(state: RecordingState) {
        serviceScope.launch(Dispatchers.Main) {
            when (state) {
                RecordingState.IDLE -> {
                    iconMic?.visibility = View.VISIBLE
                    iconStop?.visibility = View.GONE
                    progressIndicator?.visibility = View.GONE
                    overlayBackground?.setBackgroundResource(R.drawable.overlay_button_background)
                }
                RecordingState.RECORDING -> {
                    iconMic?.visibility = View.GONE
                    iconStop?.visibility = View.VISIBLE
                    progressIndicator?.visibility = View.GONE
                    overlayBackground?.setBackgroundResource(R.drawable.overlay_button_recording)
                    // No animation - just color change to indicate recording
                }
                RecordingState.PROCESSING -> {
                    iconMic?.visibility = View.GONE
                    iconStop?.visibility = View.GONE
                    progressIndicator?.visibility = View.VISIBLE
                    overlayBackground?.setBackgroundResource(R.drawable.overlay_button_background)
                }
            }
        }
    }

    private fun removeOverlay() {
        try {
            if (overlayView != null) {
                windowManager.removeView(overlayView)
                overlayView = null
                Log.d(TAG, "Overlay removed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove overlay", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        removeOverlay()
        serviceScope.cancel()
        Log.d(TAG, "OverlayService destroyed")
    }
}
