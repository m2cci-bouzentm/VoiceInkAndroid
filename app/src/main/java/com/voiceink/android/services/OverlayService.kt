package com.voiceink.android.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.voiceink.android.data.history.TranscriptionHistoryRepository
import com.voiceink.android.data.preferences.SettingsRepository
import com.voiceink.android.domain.model.PredefinedModels
import com.voiceink.android.domain.model.LocalModel
import com.voiceink.android.domain.model.TranscriptionModel
import com.voiceink.android.domain.postprocessing.AutoPunctuationService
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
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast

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
        const val ACTION_TOGGLE_RECORDING = "com.voiceink.android.TOGGLE_RECORDING"

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

    @Inject
    lateinit var historyRepository: TranscriptionHistoryRepository

    @Inject
    lateinit var autoPunctuationService: AutoPunctuationService

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentRecordingFile: File? = null
    private var isTranscribing = false

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
    private val tapHandler = Handler(Looper.getMainLooper())
    private val singleTapRunnable = Runnable {
        lastTapTime = 0L
        toggleRecording()
    }

    // Long-press detection for cancel/clear
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressTimeout = 500L // milliseconds
    private var isLongPressTriggered = false
    private val longPressRunnable = Runnable {
        isLongPressTriggered = true
        lastTapTime = 0L
        tapHandler.removeCallbacks(singleTapRunnable)
        if (audioRecorder.state.value == RecordingState.RECORDING) {
            abortRecording()
        } else {
            clearFocusedInput()
        }
    }
    
    // Broadcast receiver for volume button shortcut
    private val toggleRecordingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_TOGGLE_RECORDING) {
                Log.d(TAG, "Received toggle recording broadcast from volume shortcut")
                toggleRecording()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        
        // Register broadcast receiver for volume shortcut
        val filter = IntentFilter(ACTION_TOGGLE_RECORDING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(toggleRecordingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(toggleRecordingReceiver, filter)
        }
        
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

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = if (audioRecorder.hasPermission()) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0
                }
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    serviceType
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            stopSelf()
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
                isLongPressTriggered = false
                tapHandler.removeCallbacks(singleTapRunnable)
                
                // Start long-press timer for cancel/clear
                longPressHandler.postDelayed(longPressRunnable, longPressTimeout)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = (event.rawX - initialTouchX).toInt()
                val deltaY = (event.rawY - initialTouchY).toInt()

                if (!isDragging && (kotlin.math.abs(deltaX) > dragThreshold || kotlin.math.abs(deltaY) > dragThreshold)) {
                    isDragging = true
                    // Cancel long-press if user starts dragging
                    longPressHandler.removeCallbacks(longPressRunnable)
                    tapHandler.removeCallbacks(singleTapRunnable)
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
                // Cancel long-press timer
                longPressHandler.removeCallbacks(longPressRunnable)
                tapHandler.removeCallbacks(singleTapRunnable)
                
                // If long-press was triggered, don't process as tap
                if (isLongPressTriggered) {
                    isLongPressTriggered = false
                    return true
                }
                
                if (!isDragging) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastTapTime < doubleTapTimeout) {
                        // Double-tap detected - open IME picker
                        Log.d(TAG, "Double-tap detected, opening IME picker")
                        tapHandler.removeCallbacks(singleTapRunnable)
                        lastTapTime = 0L
                        openImePicker()
                    } else {
                        // Single tap - debounce to allow a second tap
                        lastTapTime = currentTime
                        tapHandler.postDelayed(singleTapRunnable, doubleTapTimeout)
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                // Cancel long-press timer on touch cancel
                longPressHandler.removeCallbacks(longPressRunnable)
                tapHandler.removeCallbacks(singleTapRunnable)
                return true
            }
        }
        return false
    }

    private fun toggleRecording() {
        serviceScope.launch {
            if (isTranscribing) {
                Toast.makeText(
                    this@OverlayService,
                    "Transcription in progress. Please wait.",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            when (audioRecorder.state.value) {
                RecordingState.IDLE -> {
                    if (!audioRecorder.hasPermission()) {
                        Toast.makeText(
                            this@OverlayService,
                            "Microphone permission required",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        startRecording()
                    }
                }
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
            isTranscribing = true
            try {
                transcribeAudio(audioFile)
            } finally {
                // Always reset to idle after transcription (success or failure)
                updateOverlayUI(RecordingState.IDLE)
                isTranscribing = false
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
                    var finalText = result.text
                    var hadAutoPunctuation = false

                    if (model is LocalModel && settingsRepository.autoPunctuationEnabled.first()) {
                        finalText = autoPunctuationService.punctuate(result.text)
                        hadAutoPunctuation = finalText != result.text
                    }

                    Log.d(TAG, "Transcription successful: $finalText")

                    // Save to history before deleting the audio file
                    saveToHistory(finalText, model, audioFile, hadAutoPunctuation)

                    // Try to inject text into focused field
                    if (TextInjectionService.isServiceEnabled()) {
                        val injected = TextInjectionService.injectText(finalText)
                        if (injected) {
                            Log.d(TAG, "Text injected successfully")
                        } else {
                            Log.d(TAG, "Text injection failed, copying to clipboard")
                            copyToClipboard(finalText)
                        }
                    } else {
                        // Accessibility not enabled, copy to clipboard
                        copyToClipboard(finalText)
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

    private suspend fun saveToHistory(
        text: String,
        model: TranscriptionModel,
        audioFile: File,
        hadAutoPunctuation: Boolean
    ) {
        try {
            historyRepository.save(
                text = text,
                model = model,
                audioFile = audioFile,
                wasStreaming = false,
                hadAutoPunctuation = hadAutoPunctuation
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to history", e)
        }
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
            if (!cleared) {
                Toast.makeText(this, "No editable field found", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d(TAG, "Accessibility service not enabled, cannot clear input")
            Toast.makeText(this, "Enable Text Injection to clear input", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openImePicker() {
        try {
            val intent = Intent(this, com.voiceink.android.ui.ImePickerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch IME picker activity", e)
            Toast.makeText(this, "Unable to open keyboard picker", Toast.LENGTH_SHORT).show()
        }
        tapHandler.postDelayed({ ensureOverlayVisible() }, 300L)
    }

    private fun ensureOverlayVisible() {
        val view = overlayView ?: return
        if (view.isAttachedToWindow) return
        try {
            windowManager.addView(view, layoutParams)
            Log.d(TAG, "Overlay re-attached after IME picker")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to re-attach overlay view", e)
        }
    }

    private fun abortRecording() {
        serviceScope.launch {
            val cancelled = audioRecorder.cancelRecording()
            if (cancelled) {
                Log.d(TAG, "Recording aborted via long-press")
                // Haptic feedback
                vibrateDevice()
                // Show brief toast
                Toast.makeText(this@OverlayService, "Recording cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun vibrateDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(100)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vibrate", e)
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
        
        // Unregister broadcast receiver
        try {
            unregisterReceiver(toggleRecordingReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Receiver already unregistered", e)
        }
        
        Log.d(TAG, "OverlayService destroyed")
    }
}
